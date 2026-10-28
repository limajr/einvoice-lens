package io.github.einvoicelens.core;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static io.github.einvoicelens.core.InvoiceDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class InvoiceReaderTest {
    private final InvoiceReader reader = new InvoiceReader();
    private byte[] sample(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream("/samples/" + path)) {
            assertNotNull(stream); return stream.readAllBytes();
        }
    }
    private String standard(String syntax) throws Exception {
        return new String(sample("kosit/business-cases/standard/01.01a-INVOICE_" + syntax + ".xml"), StandardCharsets.UTF_8);
    }
    private InvoiceDocument read(String xml) {
        var result = reader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertTrue(result.document().isPresent(), result.message()); return result.document().orElseThrow();
    }
    private InvoiceDocument fixture(String path) throws Exception {
        var result = reader.read(sample(path)); assertTrue(result.document().isPresent(), result.message());
        return result.document().orElseThrow();
    }

    @Test void equivalentStandardUblAndCiiHaveTheSameBusinessValues() throws Exception {
        var ubl = read(standard("ubl")); var cii = read(standard("uncefact"));
        for (var term : List.of("BT-1", "BT-3", "BT-5", "BT-10")) assertEquals(field(ubl.header(), term).raw(), field(cii.header(), term).raw(), term);
        assertEquals(LocalDate.of(2016, 4, 4), field(ubl.header(), "BT-2").date().orElseThrow());
        assertEquals(field(ubl.header(), "BT-2").date(), field(cii.header(), "BT-2").date());
        assertEquals("20160404", field(cii.header(), "BT-2").raw());
        assertEquals(field(ubl.seller().fields(), "BT-27").raw(), field(cii.seller().fields(), "BT-27").raw());
        assertEquals(field(ubl.buyer().fields(), "BT-44").raw(), field(cii.buyer().fields(), "BT-44").raw());
        assertEquals(2, ubl.lines().size()); assertEquals(2, cii.lines().size());
        for (int i = 0; i < 2; i++) for (String term : List.of("BT-126", "BT-153", "BT-129", "BT-146", "BT-131")) {
            assertEquals(field(ubl.lines().get(i).fields(), term).display(), field(cii.lines().get(i).fields(), term).display(), term);
        }
        assertEquals(field(ubl.vat().getFirst().fields(), "BT-117").number(), field(cii.vat().getFirst().fields(), "BT-117").number());
        for (String term : List.of("BT-106", "BT-109", "BT-110", "BT-112", "BT-115")) assertEquals(field(ubl.totals(), term).display(), field(cii.totals(), term).display(), term);
        assertEquals("336.9", field(ubl.totals(), "BT-115").raw());
        assertEquals("DE79000000001234567890", field(cii.payments().getFirst().fields(), "BT-84").raw());
    }

    @TestFactory Stream<DynamicTest> minimalAndComprehensiveInputsRemainReadable() {
        return Stream.of("ubl", "uncefact").flatMap(s -> Stream.of("01.01_comprehensive_test_", "01.05_minimal_test_")
                .map(name -> DynamicTest.dynamicTest(name + s, () -> {
                    var invoice = fixture("kosit/technical-cases/cius/" + name + s + ".xml");
                    assertFalse(invoice.lines().isEmpty());
                    if (name.contains("comprehensive")) {
                        assertEquals(2, invoice.payments().stream().filter(g -> g.title().startsWith("Payment option")).count());
                        assertEquals(4, invoice.adjustments().size());
                        assertEquals(4, invoice.lines().getFirst().adjustments().size());
                        assertEquals(2, invoice.vat().size());
                        assertEquals("EUR", field(invoice.totals(), "BT-110").unit());
                        assertEquals("GBP", field(invoice.totals(), "BT-111").unit());
                        assertEquals(2, invoice.attachmentCount());
                    }
                })));
    }

    @TestFactory Stream<DynamicTest> invalidInvoicesPreserveMissingAndInconsistentSourceValues() {
        return Stream.of("ubl", "uncefact").map(s -> DynamicTest.dynamicTest(s, () -> {
            var missing = fixture("derived/missing-bt-10-" + s + ".xml");
            assertEquals(State.MISSING, field(missing.header(), "BT-10").state());
            assertEquals(2, missing.lines().size());
            var total = fixture("derived/incorrect-bt-112-" + s + ".xml");
            assertEquals("336.91", field(total.totals(), "BT-112").raw());
            assertEquals("336.9", field(total.totals(), "BT-115").raw());
        }));
    }

    @Test void malformedFieldsDoNotPreventTheRestOfTheInvoiceBeingRead() throws Exception {
        var invoice = read(standard("ubl").replace("2016-04-04", "2026-02-30").replace(">336.9<", ">not-a-number<"));
        assertEquals(State.UNPARSEABLE, field(invoice.header(), "BT-2").state());
        assertEquals("2026-02-30", field(invoice.header(), "BT-2").raw());
        assertEquals(State.UNPARSEABLE, field(invoice.totals(), "BT-115").state());
        assertEquals("not-a-number", field(invoice.totals(), "BT-115").raw());
        assertEquals(2, invoice.lines().size()); assertFalse(invoice.readingIssues().isEmpty());
    }

    @Test void precisionSignsAndNonDefaultPriceBaseArePreserved() throws Exception {
        var invoice = read(standard("ubl").replace(">288.79<", ">-001.2300<")
                .replace("</cac:Price>", "<cbc:BaseQuantity unitCode='XPP'>100</cbc:BaseQuantity></cac:Price>"));
        var price = field(invoice.lines().getFirst().fields(), "BT-146");
        assertEquals("-001.2300", price.raw()); assertEquals(new BigDecimal("-1.2300"), price.number().orElseThrow());
        assertEquals(4, price.number().orElseThrow().scale());
        assertEquals("100 XPP", field(invoice.lines().getFirst().fields(), "BT-149").display());
    }

    @Test void ciiDatesRequireTheDeclaredFormat() throws Exception {
        var invoice = read(standard("uncefact").replace("format=\"102\"", "format=\"999\""));
        assertEquals(State.UNPARSEABLE, field(invoice.header(), "BT-2").state());
        assertEquals("20160404", field(invoice.header(), "BT-2").raw());
    }

    @Test void malformedCiiDateLexicalValueIsNotNormalized() throws Exception {
        var invoice = read(standard("uncefact").replace("20160404", "20160404Z"));
        assertEquals(State.UNPARSEABLE, field(invoice.header(), "BT-2").state());
        assertEquals("20160404Z", field(invoice.header(), "BT-2").raw());
    }

    @Test void nonIbanPaymentAccountIsStillReadable() throws Exception {
        var invoice = read(standard("uncefact").replace("ram:IBANID", "ram:ProprietaryID"));
        assertEquals("DE79000000001234567890", field(invoice.payments().getFirst().fields(), "BT-84").raw());
    }

    @Test void differentNamespacePrefixesHaveNoEffect() throws Exception {
        var invoice = read(standard("ubl").replace("cbc:", "basic:").replace("xmlns:cbc=", "xmlns:basic=")
                .replace("cac:", "aggregate:").replace("xmlns:cac=", "xmlns:aggregate="));
        assertEquals("123456XX", field(invoice.header(), "BT-1").raw()); assertEquals(2, invoice.lines().size());
    }

    @Test void duplicatesAreFlaggedRatherThanSilentlyDiscarded() throws Exception {
        var invoice = read(standard("ubl").replace("<cbc:ID>123456XX</cbc:ID>", "<cbc:ID>123456XX</cbc:ID><cbc:ID>another</cbc:ID>"));
        assertEquals("123456XX", field(invoice.header(), "BT-1").raw());
        assertTrue(invoice.readingIssues().stream().anyMatch(s -> s.contains("BT-1") && s.contains("more than once")));
    }

    @Test void xmlAndEmbeddedMarkupAreKeptAsText() throws Exception {
        var invoice = read(standard("ubl").replace("[Seller name]", "&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertEquals("<script>alert(1)</script>", field(invoice.seller().fields(), "BT-27").raw());
    }

    @TestFactory Stream<DynamicTest> unsafeInputIsNeverRendered() {
        return Stream.of("<broken>", "<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><x>&a;</x>",
                "<x xmlns:xi='http://www.w3.org/2001/XInclude'><xi:include href='file:///etc/passwd'/></x>",
                "<?xml-stylesheet href='http://127.0.0.1:9/a.xsl'?><x/>", "<x>".repeat(65) + "</x>".repeat(65))
                .map(xml -> DynamicTest.dynamicTest("Reject unsafe XML " + xml.length(), () -> assertTrue(reader.read(xml.getBytes(StandardCharsets.UTF_8)).document().isEmpty())));
    }

    @Test void unsupportedAndOversizeInputsHaveNoDisplayModel() {
        assertTrue(reader.read("<invoice/>".getBytes(StandardCharsets.UTF_8)).document().isEmpty());
        assertTrue(reader.read(new byte[XmlPreflight.MAX_BYTES + 1]).document().isEmpty());
    }

    @Test void noteSubjectsAreSeparatedWithoutLosingRawText() throws Exception {
        var ubl = read(standard("ubl")); var cii = read(standard("uncefact"));
        assertEquals("ADU", ubl.notes().getFirst().subject());
        assertEquals("ADU", cii.notes().getFirst().subject());
        assertEquals(ubl.notes().getFirst().text(), cii.notes().getFirst().text());
        assertTrue(ubl.notes().getFirst().raw().startsWith("#ADU#"));
        var plain = read(standard("ubl").replace("#ADU#", "#AB#"));
        assertEquals("", plain.notes().getFirst().subject()); assertTrue(plain.notes().getFirst().text().startsWith("#AB#"));
    }

    @Test void exactLineComparisonDoesNotRepairOrRoundAmounts() throws Exception {
        var valid = read(standard("ubl"));
        assertEquals(LineTotalCheck.Status.MATCH, LineTotalCheck.of(valid).status());
        var differing = read(standard("ubl").replace("<cbc:LineExtensionAmount currencyID=\"EUR\">288.79", "<cbc:LineExtensionAmount currencyID=\"EUR\">288.791"));
        var check = LineTotalCheck.of(differing);
        assertEquals(LineTotalCheck.Status.MISMATCH, check.status());
        assertEquals(new BigDecimal("314.861"), check.sum().orElseThrow());
        assertEquals(new BigDecimal("314.86"), field(differing.totals(), "BT-106").number().orElseThrow());
        var bad = read(standard("ubl").replace("<cbc:LineExtensionAmount currencyID=\"EUR\">288.79", "<cbc:LineExtensionAmount currencyID=\"EUR\">invalid"));
        assertEquals(LineTotalCheck.Status.UNAVAILABLE, LineTotalCheck.of(bad).status());
        var otherCurrency = read(standard("ubl").replace("<cbc:DocumentCurrencyCode>EUR", "<cbc:DocumentCurrencyCode>USD"));
        assertEquals(LineTotalCheck.Status.UNAVAILABLE, LineTotalCheck.of(otherCurrency).status());
    }

    @Test void readingDoesNotDependOnAValidVerdictOrWorkingValidator() throws Exception {
        try (var service = new ValidationService(bytes -> { throw new IllegalStateException("private invoice"); }, Duration.ofSeconds(5))) {
            var result = service.analyze(new ByteArrayInputStream(standard("ubl").getBytes(StandardCharsets.UTF_8)));
            assertEquals(ValidationStatus.INCOMPLETE, result.validation().status());
            assertTrue(result.reading().document().isPresent());
            assertEquals("123456XX", field(result.reading().document().orElseThrow().header(), "BT-1").raw());
        }
    }
}
