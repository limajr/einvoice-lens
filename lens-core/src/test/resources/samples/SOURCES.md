# Invoice fixture sources

## Pinned KoSIT subset

Source: [itplr-kosit/xrechnung-testsuite](https://github.com/itplr-kosit/xrechnung-testsuite)
Tag: `v2026-08-31`; commit: `44c3905c69ca236aa3b94c04be1b7e9bd066ba03`.
License: Apache-2.0; the unmodified upstream license is in `kosit/LICENSE`.
The pinned tree has no separate NOTICE file. Upstream XML files are copied byte-for-byte,
including any existing comments/notices. This fixture license is separate from the
project's MIT license. The modified copies in `derived/` retain that upstream license.

`manifest.json` records original paths, SHA-256 checksums, derivations, tool download URLs
and hashes, and experimentally verified report outcomes. Original URLs are formed as:
`https://github.com/itplr-kosit/xrechnung-testsuite/blob/44c3905c69ca236aa3b94c04be1b7e9bd066ba03/<upstreamPath>`.

| Files (each in UBL and CII) | Purpose |
| --- | --- |
| `business-cases/standard/01.01a-INVOICE_*` | Standard two-line invoice; baseline for mutations |
| `technical-cases/cius/01.01_comprehensive_test_*` | Rich parser fixture with repeated groups and attachments |
| `technical-cases/cius/01.05_minimal_test_*` | Minimal invoice |

This is an initial six-file subset, not the full official corpus. The comprehensive
examples do not individually cover all business terms: upstream has several variants
for mutually exclusive fields. Additional comprehensive, credit-note, extension and
negative cases should be selected when their behavior is implemented. See the
[upstream overview](https://github.com/itplr-kosit/xrechnung-testsuite/blob/44c3905c69ca236aa3b94c04be1b7e9bd066ba03/src/doc/test-overview.md).

## Derived invalid fixtures

Created by einvoice-lens on 2026-10-28 from the standard `01.01a` files. A comment in
each XML identifies the modification; all other source content is retained.

- `missing-bt-10-*`: remove the single BuyerReference element. Observed error: BR-DE-15.
- `incorrect-bt-112-*`: increase TaxInclusiveAmount (UBL) or GrandTotalAmount (CII) by
  0.01 without changing other totals. Observed errors: BR-CO-15 **and** BR-CO-16.

Do not assume a mutation triggers exactly one rule. Wrong VAT category cases are deferred
until a precise mutation and its actual downstream findings have been verified.

## Verified outcomes and reproduction

Checked with KoSIT Validator **1.6.3** and the XRechnung **3.0.2** configuration release
**v2026-08-31**. The configuration contains EN16931 validation **1.3.16** and XRechnung
Schematron **2.6.0**. All six upstream examples are accepted; all four mutations rejected.
The comprehensive CII example reports CII-SR-475 and CII-SR-476 warnings. Standard and
minimal examples report BR-DE-TMP-32 at information level. The manifest preserves the
KoSIT acceptance decision separately from its report-level `valid` attribute.

From the repository root, with Java 21 and Python 3.9+:

```sh
python3 scripts/check-fixtures.py --download-tools
```

This explicitly downloads only the pinned validator and configuration into ignored
`lens-core/target/fixture-tools/`, verifies their hashes and runs all ten cases. Reports
are temporary. Later runs use cached tools. To avoid downloads, supply existing artifacts:

```sh
python3 scripts/check-fixtures.py --validator /path/to/validator.jar --configuration /path/to/configuration.zip
```

This script checks the upstream CLI and corpus. Separately, `mvn verify` runs the Java
adapter against these ten fixtures and checks malformed/adversarial input, unknown
profiles, size limits and deadline/concurrency behavior. Full corpus coverage remains
future work.

## Additional candidate sources

- [ConnectingEurope/eInvoicing-EN16931](https://github.com/ConnectingEurope/eInvoicing-EN16931):
  useful for European-rule-only tests. Review the selected artifacts' license before
  vendoring; currently none copied separately.
- [Mustang](https://github.com/ZUGFeRD/mustangproject): select PDF/XML edge cases in v0.2.
  A repository license alone is not proof that every contributed real-world invoice is
  synthetic or free of personal data. Review each selected file and any specific notices.
- [FeRD](https://www.ferd-net.de/standards/zugferd): defer PDF packages to v0.2. Review
  package terms before distributing or automating downloads; a download script does not
  replace applicable license obligations. No FeRD files or speculative download URLs added.

Routine tests must use local fixtures. External downloads are explicit setup/update
steps, not a runtime requirement of the application.
