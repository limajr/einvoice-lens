# Bundled UI demo invoices

These files are exact copies of the verified test corpus, not real customer invoices.
Both are redistributed under Apache-2.0; the upstream license is included in LICENSE.

- `valid-xrechnung-ubl.xml`: KoSIT `src/test/business-cases/standard/01.01a-INVOICE_ubl.xml`.
- `invalid-missing-buyer-reference-ubl.xml`: the project's derived `missing-bt-10-ubl.xml`,
  with BT-10 removed. The modification comment is retained. Expected error: BR-DE-15.

Source: https://github.com/itplr-kosit/xrechnung-testsuite/tree/v2026-08-31
Pinned commit: `44c3905c69ca236aa3b94c04be1b7e9bd066ba03`.
Full provenance and hashes: `lens-core/src/test/resources/samples/manifest.json`.

The UI sends both examples through the same bounded ValidationService as uploaded files.
No verdicts are hardcoded. The valid example also has an informational BR-DE-TMP-32 finding.
When updating the canonical fixtures, refresh these copies and run BundledDemoInvoicesTest.
