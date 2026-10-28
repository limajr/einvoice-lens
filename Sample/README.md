# Sample invoices for manual testing

Start with **[valid-standard-ubl.xml](valid-standard-ubl.xml)** or
**[valid-standard-cii.xml](valid-standard-cii.xml)**. Open einvoice-lens at
<http://localhost:8080>, then upload a file from this folder. Use **Choose another file** or remove the current file before trying the next one.

These are public test examples, not invoices for payment. `cii` in these filenames
corresponds to `uncefact` in the upstream fixture names. All files are standard
XRechnung 3.0 XML, including the deliberately invalid variants.

The **Invoice** tab now shows the same files as readable invoices, including invalid
ones where parsing can recover their fields. Use **Validation** to review the rule
findings. The missing-reference examples display “Not provided” for Buyer reference;
the incorrect-total examples retain the inconsistent supplied totals.

## What to expect

Expectations below apply to KoSIT Validator 1.6.3 and the bundled XRechnung 3.0.2
configuration dated 2026-08-31. Informational findings do not change a valid verdict.
The comprehensive CII example intentionally demonstrates accepted-with-warnings behavior.

| File | UI verdict | What it exercises | Expected rule IDs |
| --- | --- | --- | --- |
| [valid-standard-cii.xml](valid-standard-cii.xml) | Valid | Standard two-line invoice | `BR-DE-TMP-32` |
| [valid-standard-ubl.xml](valid-standard-ubl.xml) | Valid | Standard two-line invoice | `BR-DE-TMP-32` |
| [valid-minimal-cii.xml](valid-minimal-cii.xml) | Valid | Minimal invoice | `BR-DE-TMP-32` |
| [valid-minimal-ubl.xml](valid-minimal-ubl.xml) | Valid | Minimal invoice | `BR-DE-TMP-32` |
| [valid-comprehensive-ubl.xml](valid-comprehensive-ubl.xml) | Valid | Rich invoice with repeated groups and attachments | None |
| [warnings-comprehensive-cii.xml](warnings-comprehensive-cii.xml) | Valid with warnings | Rich invoice with repeated groups and attachments | `CII-SR-475`, `CII-SR-476` |
| [invalid-incorrect-total-cii.xml](invalid-incorrect-total-cii.xml) | Invalid | Total including VAT (BT-112) increased by one cent | `BR-CO-15`, `BR-CO-16`, `BR-DE-TMP-32` |
| [invalid-incorrect-total-ubl.xml](invalid-incorrect-total-ubl.xml) | Invalid | Total including VAT (BT-112) increased by one cent | `BR-CO-15`, `BR-CO-16`, `BR-DE-TMP-32` |
| [invalid-missing-buyer-reference-cii.xml](invalid-missing-buyer-reference-cii.xml) | Invalid | Buyer reference (BT-10) removed | `BR-DE-15`, `BR-DE-TMP-32` |
| [invalid-missing-buyer-reference-ubl.xml](invalid-missing-buyer-reference-ubl.xml) | Invalid | Buyer reference (BT-10) removed | `BR-DE-15`, `BR-DE-TMP-32` |

The comprehensive files contain repeated groups and embedded attachments, useful for
future invoice-view testing. They do not individually cover every business term.
This folder currently contains XML only; ZUGFeRD/Factur-X PDFs are planned for v0.2.

## Origin and license

Files are byte-for-byte copies of the project's verified test fixtures, with easier
filenames. The original KoSIT examples and derived invalid variants retain the
**Apache-2.0** license in [LICENSE](LICENSE); the project's MIT license does not replace it.
Modification comments in the invalid XML files are preserved.

Upstream: [KoSIT XRechnung test suite, v2026-08-31](https://github.com/itplr-kosit/xrechnung-testsuite/tree/v2026-08-31),
commit `44c3905c69ca236aa3b94c04be1b7e9bd066ba03`.
For checksums, original upstream paths, mutation details and reproduction instructions,
see the [fixture manifest](../lens-core/src/test/resources/samples/manifest.json) and
[fixture sources](../lens-core/src/test/resources/samples/SOURCES.md).

| Manual sample | Canonical test fixture |
| --- | --- |
| `valid-standard-cii.xml` | [kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml](../lens-core/src/test/resources/samples/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml) |
| `valid-standard-ubl.xml` | [kosit/business-cases/standard/01.01a-INVOICE_ubl.xml](../lens-core/src/test/resources/samples/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml) |
| `valid-minimal-cii.xml` | [kosit/technical-cases/cius/01.05_minimal_test_uncefact.xml](../lens-core/src/test/resources/samples/kosit/technical-cases/cius/01.05_minimal_test_uncefact.xml) |
| `valid-minimal-ubl.xml` | [kosit/technical-cases/cius/01.05_minimal_test_ubl.xml](../lens-core/src/test/resources/samples/kosit/technical-cases/cius/01.05_minimal_test_ubl.xml) |
| `valid-comprehensive-ubl.xml` | [kosit/technical-cases/cius/01.01_comprehensive_test_ubl.xml](../lens-core/src/test/resources/samples/kosit/technical-cases/cius/01.01_comprehensive_test_ubl.xml) |
| `warnings-comprehensive-cii.xml` | [kosit/technical-cases/cius/01.01_comprehensive_test_uncefact.xml](../lens-core/src/test/resources/samples/kosit/technical-cases/cius/01.01_comprehensive_test_uncefact.xml) |
| `invalid-incorrect-total-cii.xml` | [derived/incorrect-bt-112-uncefact.xml](../lens-core/src/test/resources/samples/derived/incorrect-bt-112-uncefact.xml) |
| `invalid-incorrect-total-ubl.xml` | [derived/incorrect-bt-112-ubl.xml](../lens-core/src/test/resources/samples/derived/incorrect-bt-112-ubl.xml) |
| `invalid-missing-buyer-reference-cii.xml` | [derived/missing-bt-10-uncefact.xml](../lens-core/src/test/resources/samples/derived/missing-bt-10-uncefact.xml) |
| `invalid-missing-buyer-reference-ubl.xml` | [derived/missing-bt-10-ubl.xml](../lens-core/src/test/resources/samples/derived/missing-bt-10-ubl.xml) |

When updating a canonical fixture, refresh its copy here and review the expected verdict
and rules above against the pinned configuration. Keep personal experiments outside this
folder so real invoice data does not enter the repository.
