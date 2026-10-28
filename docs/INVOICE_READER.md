# Readable invoice scope

The reader supports standard XRechnung 3.0 UBL Invoice and CII XML. It creates a partial
display model, not an accounting document or a complete lossless XML model. Unsupported
profiles are not interpreted; a missing profile in a recognized syntax allows partial
reading while validation remains unsupported.

## Displayed fields

| Area | Business terms / coverage |
| --- | --- |
| Header | BT-1, BT-2, BT-3, BT-5, BT-9, BT-10, BT-11, BT-12, BT-13 |
| Seller/buyer | Names, trading names, postal addresses, IDs, VAT IDs, seller tax ID, electronic addresses and contacts |
| Lines | BT-126/127, BT-129 with unit, BT-131, BT-134/135, BT-146, BT-149 with unit, BT-151/152, BT-153/154, BT-155/156 |
| Allowances/charges | Document BT-92–105 and line BT-136–145 where present |
| VAT breakdown | BT-116–121, including exemption text/code |
| Totals | BT-106–115; retain separate invoice/accounting-currency VAT totals |
| Payment | Payment means, references, credit-transfer account/IBAN/name/BIC, card details, debit account/mandate/creditor ID, payee name and payment terms |
| Delivery/period | BT-72, BT-73, BT-74 dates; delivery addresses and tax-point details are not yet rendered |
| Notes/supporting documents | Invoice note text and subject codes; supporting document count, no attachment decoding/rendering/download |

Core mapping is in `InvoiceReader`. UBL uses the namespace URIs for its common basic and
aggregate components; CII uses the D16B `CrossIndustryInvoice:100`, reusable aggregate and
unqualified data-type namespaces. XPath bindings use those namespace URIs, never the
prefixes chosen by the source document. Paths are static application code, never input
expressions. The pinned KoSIT fixtures and the XSD/Schematron resources listed in
`lens-core/src/main/resources/kosit/3.0.2-2026-08-31/SOURCES.md` provide reference material.

## Source-value behavior

- Money and other numeric values carry their raw XML text and optional BigDecimal value.
  Source sign, leading zeroes and scale are retained in the model and raw-value tooltip.
  Surrounding XML whitespace is trimmed. The display formatter uses BigDecimal directly
  with rounding mode UNNECESSARY, localized separators and currency symbols. It pads to
  the currency's minor-unit digits and keeps additional meaningful digits, e.g. 1.2340
  EUR displays as €1.234 or 1,234 €, never 1.23. Unknown currencies remain explicit raw codes.
- Date values use strict ISO dates (UBL) or format 102 / eight-digit dates (CII). Interpreted
  values display as 04 Apr 2016 (English) or 04.04.2016 (German), with original XML text in
  the tooltip. Invalid values remain raw and marked as unparseable.
- Header Language / Sprache controls UI text, field labels, explanations and code labels.
  Display format independently controls dates and numbers. Its default follows the UI
  language, while an explicit format override is retained within the current UI session.
  Switching language re-renders the same immutable result without revalidating or
  translating invoice content. The selected result tab and finding filter are retained.
- lens-explain contains a source-documented subset of UNCL1001, 4461, 5305 and UNECE
  Rec20/21 labels. German labels are project-authored translations. Unknown codes fall back
  to their raw value. Labels never establish validity or a country's applicable VAT rate.
- Recognized IBAN shapes are grouped in fours; Copy sends the unspaced identifier to the
  clipboard. This is formatting, not account ownership or checksum verification.
- Leading UBL #ABC# note prefixes are separated into subject and text; CII SubjectCode
  is read separately. The original note text remains available in the tooltip.
- Repeated item description/name text is suppressed in the overview. A line identifier
  equal to its item name is also hidden in the overview and remains available in details.
- Missing selected fields have a MISSING state; malformed dates/decimals have an
  UNPARSEABLE state with raw text and a reading issue. Other fields can still be read.
- Repeated groups are preserved. If a selected scalar occurs more than once, the reader
  shows its first value and explicitly reports that multiple values were present.
- Unknown allowance/charge indicators are not assigned official allowance/charge terms.
- Some valid UBL/CII fixture pairs differ in content, especially technical examples.
  Equivalence tests compare the standard paired invoice and specific shared values.
- XML content is always rendered with Vaadin text components, including markup-like text.

## Integration and limits

`ValidationService.analyze` reads the input once into a bounded byte array, then independently
runs the validator and reader on the same bytes. A validator exception does not prevent
reading. Both operations share the existing single worker and 30-second response deadline.
The XML preflight rejects prohibited features and excessive structure before DOM parsing;
the DOM parser also disables external entities, DTDs, XInclude and external schema access.
The original XML is not retained by the invoice view.

UI field links only appear when a business term mentioned in an original finding has one
rendered scalar target. They are navigation aids, not newly inferred rule explanations.
Line-specific field navigation remains later work. An initial seven-rule plain-language
catalog is now implemented with exact bundle applicability; see the source-linked
`lens-explain/src/main/resources/EXPLANATIONS.md`. Official messages remain unchanged.

On large findings lists, the UI displays up to 100 entries per severity filter. Invoice
line tables use virtual scrolling. Clearing/removing a file discards its displayed parsed
data and findings; session expiry remains 15 minutes.

## Exact line subtotal comparison

LineTotalCheck separately compares the exact BigDecimal sum of BT-131 amounts with
BT-106. No values are replaced or rounded, and the result is not inserted into the
KoSIT finding list or verdict. Missing/malformed amounts, unknown or mixed currencies,
empty lines and ambiguous selected amounts produce UNAVAILABLE rather than a success.
A mismatch shows both the computed line sum and the supplied subtotal. This checks only
that relationship, not all invoice totals or tax calculations.
