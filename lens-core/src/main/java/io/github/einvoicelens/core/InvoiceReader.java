package io.github.einvoicelens.core;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import static io.github.einvoicelens.core.InvoiceDocument.*;

/** Reads selected EN16931 business terms; never computes, repairs or validates invoice amounts. */
public final class InvoiceReader {
    public InvoiceReading read(byte[] xml) {
        if (xml.length == 0 || xml.length > XmlPreflight.MAX_BYTES) return InvoiceReading.unavailable("Choose a non-empty XML file of at most 5 MiB.");
        try {
            var detected = XmlPreflight.inspect(xml);
            if (detected.syntax().equals("Unknown") || (!detected.profile().isBlank() && !detected.profile().equals(XmlPreflight.PROFILE))) {
                return InvoiceReading.unavailable("A readable view is available for standard XRechnung 3.0 UBL Invoice and CII XML.");
            }
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "64");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/elementAttributeLimit", "100");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            });
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("External resources are forbidden"); });
            var document = builder.parse(new ByteArrayInputStream(xml));
            var reader = new Mapping(document.getDocumentElement(), detected.syntax());
            return new InvoiceReading(Optional.of(reader.read()), "Values extracted from the XML. Totals are shown as supplied.");
        } catch (Exception e) {
            return InvoiceReading.unavailable("The XML could not be read safely. Review the validation results for details.");
        }
    }

    private static final class Mapping {
        private static final String AGREEMENT = "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/";
        private static final String SETTLEMENT = "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/";
        private static final String DELIVERY = "rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeDelivery/";
        private final Element root;
        private final boolean ubl;
        private final String syntax;
        private final XPath xpath = XPathFactory.newDefaultInstance().newXPath();
        private final Map<String, XPathExpression> expressions = new HashMap<>();
        private final Set<String> issues = new LinkedHashSet<>();
        private String currency;

        Mapping(Element root, String syntax) {
            this.root = root; this.syntax = syntax; ubl = syntax.equals("UBL Invoice");
            var namespaces = Map.of(
                    "cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
                    "cac", "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
                    "rsm", "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
                    "ram", "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
                    "udt", "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100");
            xpath.setNamespaceContext(new NamespaceContext() {
                public String getNamespaceURI(String prefix) { return namespaces.getOrDefault(prefix, XMLConstants.NULL_NS_URI); }
                public String getPrefix(String uri) { return namespaces.entrySet().stream().filter(e -> e.getValue().equals(uri)).map(Map.Entry::getKey).findFirst().orElse(null); }
                public Iterator<String> getPrefixes(String uri) { return namespaces.entrySet().stream().filter(e -> e.getValue().equals(uri)).map(Map.Entry::getKey).iterator(); }
            });
        }

        InvoiceDocument read() throws Exception {
            currency = raw(root, path("cbc:DocumentCurrencyCode", SETTLEMENT + "ram:InvoiceCurrencyCode"));
            var header = fields(root,
                    "BT-1|Invoice number|" + path("cbc:ID", "rsm:ExchangedDocument/ram:ID"),
                    "BT-2|Issue date|" + path("cbc:IssueDate", "rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString") + "|d",
                    "BT-3|Document type code|" + path("cbc:InvoiceTypeCode", "rsm:ExchangedDocument/ram:TypeCode"),
                    "BT-5|Currency|" + path("cbc:DocumentCurrencyCode", SETTLEMENT + "ram:InvoiceCurrencyCode"),
                    "BT-9|Due date|" + path("cbc:DueDate", SETTLEMENT + "ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime/udt:DateTimeString") + "|d",
                    "BT-10|Buyer reference|" + path("cbc:BuyerReference", AGREEMENT + "ram:BuyerReference"),
                    "BT-13|Purchase order reference|" + path("cac:OrderReference/cbc:ID", AGREEMENT + "ram:BuyerOrderReferencedDocument/ram:IssuerAssignedID"),
                    "BT-12|Contract reference|" + path("cac:ContractDocumentReference/cbc:ID", AGREEMENT + "ram:ContractReferencedDocument/ram:IssuerAssignedID"),
                    "BT-11|Project reference|" + path("cac:ProjectReference/cbc:ID", AGREEMENT + "ram:SpecifiedProcuringProject/ram:ID"));
            var lines = new ArrayList<Line>();
            for (var node : nodes(root, path("cac:InvoiceLine", "rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem"))) {
                lines.add(line(node));
            }
            var vat = new ArrayList<Group>();
            for (var node : nodes(root, path("cac:TaxTotal/cac:TaxSubtotal", SETTLEMENT + "ram:ApplicableTradeTax[ram:TypeCode='VAT']"))) {
                vat.add(new Group("VAT breakdown", fields(node,
                        "BT-118|VAT category|" + path("cac:TaxCategory/cbc:ID", "ram:CategoryCode"),
                        "BT-119|VAT rate|" + path("cac:TaxCategory/cbc:Percent", "ram:RateApplicablePercent") + "|p",
                        "BT-116|Taxable amount|" + path("cbc:TaxableAmount", "ram:BasisAmount") + "|m",
                        "BT-117|VAT amount|" + path("cbc:TaxAmount", "ram:CalculatedAmount") + "|m",
                        "BT-120|Exemption reason|" + path("cac:TaxCategory/cbc:TaxExemptionReason", "ram:ExemptionReason"),
                        "BT-121|Exemption reason code|" + path("cac:TaxCategory/cbc:TaxExemptionReasonCode", "ram:ExemptionReasonCode"))));
            }
            String total = path("cac:LegalMonetaryTotal/", SETTLEMENT + "ram:SpecifiedTradeSettlementHeaderMonetarySummation/");
            var totals = fields(root,
                    "BT-106|Sum of line net amounts|" + total + path("cbc:LineExtensionAmount", "ram:LineTotalAmount") + "|m",
                    "BT-107|Document allowances|" + total + path("cbc:AllowanceTotalAmount", "ram:AllowanceTotalAmount") + "|m",
                    "BT-108|Document charges|" + total + path("cbc:ChargeTotalAmount", "ram:ChargeTotalAmount") + "|m",
                    "BT-109|Total excluding VAT|" + total + path("cbc:TaxExclusiveAmount", "ram:TaxBasisTotalAmount") + "|m",
                    "BT-112|Total including VAT|" + total + path("cbc:TaxInclusiveAmount", "ram:GrandTotalAmount") + "|m",
                    "BT-113|Amount already paid|" + total + path("cbc:PrepaidAmount", "ram:TotalPrepaidAmount") + "|m",
                    "BT-114|Rounding amount|" + total + path("cbc:PayableRoundingAmount", "ram:RoundingAmount") + "|m",
                    "BT-115|Amount due|" + total + path("cbc:PayableAmount", "ram:DuePayableAmount") + "|m");
            for (var tax : nodes(root, path("cac:TaxTotal/cbc:TaxAmount", total + "ram:TaxTotalAmount"))) {
                String unit = tax.getAttribute("currencyID");
                boolean accounting = !unit.isBlank() && !currency.isBlank() && !unit.equals(currency);
                totals.add(value(tax, accounting ? "BT-111" : "BT-110", accounting ? "VAT in accounting currency" : "Total VAT", "m"));
            }
            if (totals.stream().noneMatch(f -> f.term().equals("BT-110"))) totals.add(value(null, "BT-110", "Total VAT", "m"));
            totals.sort(Comparator.comparingInt(field -> Integer.parseInt(field.term().substring(3))));
            var payments = new ArrayList<Group>();
            for (var node : nodes(root, path("cac:PaymentMeans", SETTLEMENT + "ram:SpecifiedTradeSettlementPaymentMeans"))) {
                var values = fields(node,
                        "BT-81|Payment means code|" + path("cbc:PaymentMeansCode", "ram:TypeCode"),
                        "BT-82|Payment means text|" + path("cbc:PaymentMeansCode/@name", "ram:Information"),
                        "BT-84|Payment account / IBAN|" + path("cac:PayeeFinancialAccount/cbc:ID", "ram:PayeePartyCreditorFinancialAccount/*[self::ram:IBANID or self::ram:ProprietaryID]"),
                        "BT-85|Account name|" + path("cac:PayeeFinancialAccount/cbc:Name", "ram:PayeePartyCreditorFinancialAccount/ram:AccountName"),
                        "BT-86|BIC / institution identifier|" + path("cac:PayeeFinancialAccount/cac:FinancialInstitutionBranch/cbc:ID", "ram:PayeeSpecifiedCreditorFinancialInstitution/ram:BICID"),
                        "BT-87|Card account number|" + path("cac:CardAccount/cbc:PrimaryAccountNumberID", "ram:ApplicableTradeSettlementFinancialCard/ram:ID"),
                        "BT-88|Cardholder name|" + path("cac:CardAccount/cbc:HolderName", "ram:ApplicableTradeSettlementFinancialCard/ram:CardholderName"),
                        "BT-91|Debited account / IBAN|" + path("cac:PaymentMandate/cac:PayerFinancialAccount/cbc:ID", "ram:PayerPartyDebtorFinancialAccount/ram:IBANID"));
                values.addAll(fields(ubl ? node : root,
                        "BT-83|Payment reference|" + path("cbc:PaymentID", SETTLEMENT + "ram:PaymentReference"),
                        "BT-89|Mandate reference|" + path("cac:PaymentMandate/cbc:ID", SETTLEMENT + "ram:SpecifiedTradePaymentTerms/ram:DirectDebitMandateID")));
                payments.add(new Group("Payment option " + (payments.size() + 1), values));
            }
            var paymentContext = fields(root,
                    "BT-59|Payee name|" + path("cac:PayeeParty/cac:PartyName/cbc:Name", SETTLEMENT + "ram:PayeeTradeParty/ram:Name"),
                    "BT-90|Creditor identifier|" + path("cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID[@schemeID='SEPA']", SETTLEMENT + "ram:CreditorReferenceID"));
            for (var note : nodes(root, path("cac:PaymentTerms/cbc:Note", SETTLEMENT + "ram:SpecifiedTradePaymentTerms/ram:Description"))) {
                paymentContext.add(value(note, "BT-20", "Payment terms", "t"));
            }
            if (paymentContext.stream().anyMatch(f -> f.state() != State.MISSING)) payments.add(new Group("Payment terms and payee", paymentContext));
            var delivery = fields(root,
                    "BT-72|Delivery date|" + path("cac:Delivery/cbc:ActualDeliveryDate", DELIVERY + "ram:ActualDeliverySupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString") + "|d",
                    "BT-73|Invoicing period start|" + path("cac:InvoicePeriod/cbc:StartDate", SETTLEMENT + "ram:BillingSpecifiedPeriod/ram:StartDateTime/udt:DateTimeString") + "|d",
                    "BT-74|Invoicing period end|" + path("cac:InvoicePeriod/cbc:EndDate", SETTLEMENT + "ram:BillingSpecifiedPeriod/ram:EndDateTime/udt:DateTimeString") + "|d");
            var notes = new ArrayList<Note>();
            if (ubl) {
                for (var note : nodes(root, "cbc:Note")) {
                    String original = note.getTextContent().strip();
                    var prefix = java.util.regex.Pattern.compile("^#([A-Z]{3})#(.*)$", java.util.regex.Pattern.DOTALL).matcher(original);
                    notes.add(prefix.matches() ? new Note(prefix.group(1), prefix.group(2).strip(), original)
                            : new Note("", original, original));
                }
            } else {
                for (var note : nodes(root, "rsm:ExchangedDocument/ram:IncludedNote")) {
                    String subject = raw(note, "ram:SubjectCode");
                    for (var content : nodes(note, "ram:Content")) {
                        String original = content.getTextContent().strip();
                        notes.add(new Note(subject, original, original));
                    }
                }
            }
            int attachments = nodes(root, path("cac:AdditionalDocumentReference[cac:Attachment]", AGREEMENT + "ram:AdditionalReferencedDocument[ram:TypeCode='916']")).size();
            return new InvoiceDocument(syntax, header, party(true), party(false), lines, vat, totals, payments,
                    adjustments(root, path("cac:AllowanceCharge", SETTLEMENT + "ram:SpecifiedTradeAllowanceCharge"), false), delivery,
                    notes, attachments, new ArrayList<>(issues));
        }

        private Group party(boolean seller) throws Exception {
            var node = first(root, path(seller ? "cac:AccountingSupplierParty/cac:Party" : "cac:AccountingCustomerParty/cac:Party",
                    AGREEMENT + (seller ? "ram:SellerTradeParty" : "ram:BuyerTradeParty")));
            String address = path("cac:PostalAddress/", "ram:PostalTradeAddress/");
            String prefix = seller ? "Seller" : "Buyer";
            var fields = fields(node,
                    (seller ? "BT-27" : "BT-44") + "|Name|" + path("cac:PartyLegalEntity/cbc:RegistrationName", "ram:Name"),
                    (seller ? "BT-28" : "BT-45") + "|Trading name|" + path("cac:PartyName/cbc:Name", "ram:SpecifiedLegalOrganization/ram:TradingBusinessName"),
                    (seller ? "BT-35" : "BT-50") + "|Address|" + address + path("cbc:StreetName", "ram:LineOne"),
                    (seller ? "BT-36" : "BT-51") + "|Address line 2|" + address + path("cbc:AdditionalStreetName", "ram:LineTwo"),
                    (seller ? "BT-162" : "BT-163") + "|Address line 3|" + address + path("cac:AddressLine/cbc:Line", "ram:LineThree"),
                    (seller ? "BT-38" : "BT-53") + "|Postal code|" + address + path("cbc:PostalZone", "ram:PostcodeCode"),
                    (seller ? "BT-37" : "BT-52") + "|City|" + address + path("cbc:CityName", "ram:CityName"),
                    (seller ? "BT-39" : "BT-54") + "|Region|" + address + path("cbc:CountrySubentity", "ram:CountrySubDivisionName"),
                    (seller ? "BT-40" : "BT-55") + "|Country|" + address + path("cac:Country/cbc:IdentificationCode", "ram:CountryID"),
                    (seller ? "BT-31" : "BT-48") + "|VAT identifier|" + path("cac:PartyTaxScheme[cac:TaxScheme/cbc:ID='VAT']/cbc:CompanyID", "ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']"),
                    (seller ? "BT-30" : "BT-47") + "|Legal registration identifier|" + path("cac:PartyLegalEntity/cbc:CompanyID", "ram:SpecifiedLegalOrganization/ram:ID"),
                    (seller ? "BT-34" : "BT-49") + "|Electronic address|" + path("cbc:EndpointID", "ram:URIUniversalCommunication/ram:URIID"),
                    (seller ? "BT-41" : "BT-56") + "|Contact|" + path("cac:Contact/cbc:Name", "ram:DefinedTradeContact/ram:PersonName"),
                    (seller ? "BT-42" : "BT-57") + "|Telephone|" + path("cac:Contact/cbc:Telephone", "ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber"),
                    (seller ? "BT-43" : "BT-58") + "|Email|" + path("cac:Contact/cbc:ElectronicMail", "ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID"));
            if (seller) fields.addAll(fields(node, "BT-32|Tax registration identifier|" + path("cac:PartyTaxScheme[cac:TaxScheme/cbc:ID!='VAT']/cbc:CompanyID", "ram:SpecifiedTaxRegistration/ram:ID[@schemeID='FC']")));
            for (var id : nodes(node, path("cac:PartyIdentification/cbc:ID[not(@schemeID='SEPA')]", "ram:ID | ram:GlobalID"))) {
                fields.add(value(id, seller ? "BT-29" : "BT-46", "Identifier", "t"));
            }
            return new Group(prefix, fields);
        }

        private Line line(Element node) throws Exception {
            String settlement = "ram:SpecifiedLineTradeSettlement/";
            var values = fields(node,
                    "BT-126|Line identifier|" + path("cbc:ID", "ram:AssociatedDocumentLineDocument/ram:LineID"),
                    "BT-153|Item name|" + path("cac:Item/cbc:Name", "ram:SpecifiedTradeProduct/ram:Name"),
                    "BT-154|Description|" + path("cac:Item/cbc:Description", "ram:SpecifiedTradeProduct/ram:Description"),
                    "BT-129|Quantity|" + path("cbc:InvoicedQuantity", "ram:SpecifiedLineTradeDelivery/ram:BilledQuantity") + "|q",
                    "BT-146|Net unit price|" + path("cac:Price/cbc:PriceAmount", "ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount") + "|m",
                    "BT-149|Price base quantity|" + path("cac:Price/cbc:BaseQuantity", "ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:BasisQuantity") + "|q",
                    "BT-131|Line net amount|" + path("cbc:LineExtensionAmount", settlement + "ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount") + "|m",
                    "BT-127|Line note|" + path("cbc:Note", "ram:AssociatedDocumentLineDocument/ram:IncludedNote/ram:Content"),
                    "BT-155|Seller item identifier|" + path("cac:Item/cac:SellersItemIdentification/cbc:ID", "ram:SpecifiedTradeProduct/ram:SellerAssignedID"),
                    "BT-156|Buyer item identifier|" + path("cac:Item/cac:BuyersItemIdentification/cbc:ID", "ram:SpecifiedTradeProduct/ram:BuyerAssignedID"),
                    "BT-134|Line period start|" + path("cac:InvoicePeriod/cbc:StartDate", settlement + "ram:BillingSpecifiedPeriod/ram:StartDateTime/udt:DateTimeString") + "|d",
                    "BT-135|Line period end|" + path("cac:InvoicePeriod/cbc:EndDate", settlement + "ram:BillingSpecifiedPeriod/ram:EndDateTime/udt:DateTimeString") + "|d");
            var taxes = new ArrayList<Group>();
            for (var tax : nodes(node, path("cac:Item/cac:ClassifiedTaxCategory", settlement + "ram:ApplicableTradeTax[ram:TypeCode='VAT']"))) {
                taxes.add(new Group("Line VAT", fields(tax,
                        "BT-151|VAT category|" + path("cbc:ID", "ram:CategoryCode"),
                        "BT-152|VAT rate|" + path("cbc:Percent", "ram:RateApplicablePercent") + "|p")));
            }
            return new Line(values, adjustments(node, path("cac:AllowanceCharge", settlement + "ram:SpecifiedTradeAllowanceCharge"), true), taxes);
        }

        private List<Group> adjustments(Element context, String expression, boolean line) throws Exception {
            var groups = new ArrayList<Group>();
            for (var node : nodes(context, expression)) {
                String indicator = raw(node, path("cbc:ChargeIndicator", "ram:ChargeIndicator/udt:Indicator"));
                boolean charge = indicator.equals("true") || indicator.equals("1");
                boolean allowance = indicator.equals("false") || indicator.equals("0");
                String title = charge ? "Charge" : allowance ? "Allowance" : "Adjustment (unknown indicator: " + indicator + ")";
                String[] terms = line ? charge ? new String[]{"BT-141","BT-142","BT-143","BT-144","BT-145"} : new String[]{"BT-136","BT-137","BT-138","BT-139","BT-140"}
                        : charge ? new String[]{"BT-99","BT-100","BT-101","BT-104","BT-105"} : new String[]{"BT-92","BT-93","BT-94","BT-97","BT-98"};
                if (!charge && !allowance) {
                    Arrays.fill(terms, "UNCLASSIFIED");
                    issues.add("An allowance/charge indicator could not be interpreted; its values are shown without assigning business terms.");
                }
                var values = fields(node,
                        terms[0] + "|Amount|" + path("cbc:Amount", "ram:ActualAmount") + "|m",
                        terms[1] + "|Base amount|" + path("cbc:BaseAmount", "ram:BasisAmount") + "|m",
                        terms[2] + "|Percentage|" + path("cbc:MultiplierFactorNumeric", "ram:CalculationPercent") + "|p",
                        terms[3] + "|Reason|" + path("cbc:AllowanceChargeReason", "ram:Reason"),
                        terms[4] + "|Reason code|" + path("cbc:AllowanceChargeReasonCode", "ram:ReasonCode"));
                if (!line) values.addAll(fields(node,
                        (charge ? "BT-102" : allowance ? "BT-95" : "UNCLASSIFIED") + "|VAT category|" + path("cac:TaxCategory/cbc:ID", "ram:CategoryTradeTax/ram:CategoryCode"),
                        (charge ? "BT-103" : allowance ? "BT-96" : "UNCLASSIFIED") + "|VAT rate|" + path("cac:TaxCategory/cbc:Percent", "ram:CategoryTradeTax/ram:RateApplicablePercent") + "|p"));
                groups.add(new Group(title, values));
            }
            return groups;
        }

        private String path(String ublPath, String ciiPath) { return ubl ? ublPath : ciiPath; }
        private List<Element> nodes(Element node, String expression) throws Exception {
            if (node == null) return List.of();
            XPathExpression compiled = expressions.get(expression);
            if (compiled == null) { compiled = xpath.compile(expression); expressions.put(expression, compiled); }
            var result = (NodeList) compiled.evaluate(node, XPathConstants.NODESET);
            var elements = new ArrayList<Element>();
            for (int i = 0; i < result.getLength(); i++) {
                Node item = result.item(i);
                if (item instanceof Element element) elements.add(element);
                else if (item instanceof Attr attribute) {
                    var wrapper = root.getOwnerDocument().createElement("value");
                    wrapper.setTextContent(attribute.getValue()); elements.add(wrapper);
                }
            }
            return elements;
        }
        private Element first(Element node, String expression) throws Exception {
            var result = nodes(node, expression);
            return result.isEmpty() ? null : result.getFirst();
        }
        private String raw(Element node, String expression) throws Exception {
            var value = first(node, expression); return value == null ? "" : value.getTextContent().strip();
        }
        private List<Field> fields(Element node, String... specs) throws Exception {
            var values = new ArrayList<Field>();
            for (String spec : specs) {
                String[] parts = spec.split("[|]", -1);
                var selected = nodes(node, parts[2]);
                if (selected.size() > 1) issues.add(parts[1] + " (" + parts[0] + ") occurs more than once; the first value is displayed.");
                values.add(value(selected.isEmpty() ? null : selected.getFirst(), parts[0], parts[1], parts.length > 3 ? parts[3] : "t"));
            }
            return values;
        }
        private Field value(Element node, String term, String label, String kind) {
            String raw = node == null ? "" : node.getTextContent().strip();
            String unit = "";
            if (kind.equals("m")) unit = node != null && node.hasAttribute("currencyID") ? node.getAttribute("currencyID") : currency;
            if (kind.equals("q") && node != null) unit = node.getAttribute("unitCode");
            if (kind.equals("p")) unit = "%";
            if (unit == null) unit = "";
            if (raw.isEmpty()) return new Field(term, label, raw, unit, State.MISSING, Optional.empty(), Optional.empty());
            Optional<BigDecimal> number = Optional.empty(); Optional<LocalDate> date = Optional.empty();
            State state = State.PRESENT;
            try {
                if (List.of("m", "n", "q", "p").contains(kind)) {
                    if (!raw.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)")) throw new IllegalArgumentException("Not an XML decimal");
                    number = Optional.of(new BigDecimal(raw));
                }
                if (kind.equals("d")) {
                    if (!ubl && (node == null || !node.getAttribute("format").equals("102") || !raw.matches("[0-9]{8}"))) throw new IllegalArgumentException("Unsupported date format");
                    date = Optional.of(LocalDate.parse(raw, ubl ? DateTimeFormatter.ISO_LOCAL_DATE : DateTimeFormatter.BASIC_ISO_DATE));
                }
            } catch (RuntimeException e) {
                state = State.UNPARSEABLE;
                issues.add(label + " (" + term + ") could not be interpreted; the original value is displayed.");
            }
            return new Field(term, label, raw, unit, state, number, date);
        }
    }
}
