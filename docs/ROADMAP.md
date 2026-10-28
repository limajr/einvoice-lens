# Product scope and roadmap

## M0 — foundation (implemented)

Three Maven modules, Java 21, a Vaadin application shell, MIT license, Maven wrapper,
CI production build, and an executable architecture boundary test.

## M1 — XML validation (implemented preview)

Bounded UI uploads and bundled KoSIT 1.6.3 / XRechnung 3.0.2 configuration 2026-08-31.
Standard XRechnung 3.0 UBL Invoice and CII detection, original findings, locations,
per-layer outcomes and provenance. The adapter checks the ten pinned fixtures plus
security/failure cases. Full official-corpus coverage and process isolation remain open.

## M2 — readable invoice view (implemented for v0.2.0)

Namespace-aware UBL/CII readers produce an immutable, partial display model independently
of the validator. Invoice and Validation tabs show the verdict beside readable parties,
dates, references, lines, VAT, allowances/charges, source totals, payment instructions,
periods and notes. Preserve raw values, decimal scale and malformed/missing fields.
Support repeated payment options and multiple VAT currencies. Link findings to scalar
business terms when the target is unambiguous. See INVOICE_READER.md for exact scope.

The development version is 0.2.0-SNAPSHOT; see CHANGELOG.md. No release has been published.
The original version-based PDF plan has moved later to give this reader its own release.

## M4 — explanations and bilingual UI (initial catalog implemented)

Seven reviewed DE/EN entries cover the current sample findings. Each records official
rule provenance and exact bundle applicability. Explanations and suggested actions lead
findings; original rule text remains expandable. Unknown rules and unreviewed versions
fall back without invented semantics. The header language switch translates application
labels and explanations without changing source content. Check stages expose their own
severity counts and distinguish failures, warnings, information, passes and not-run states.

## Next — catalog coverage and distribution

Expand the reviewed explanation catalog beyond the seven initial rules. Expand field links where XPath locations can be mapped reliably, including
line-specific findings. Add HTML validation reports, Docker packaging, a full runtime
notice inventory and offline runtime verification before public distribution. Collect
feedback on whether users can understand and communicate an invoice error.

## Later

ZUGFeRD/Factur-X extraction, profile handling and separately reported PDF/A conformance;
bounded attachment downloads. Other candidates: CLI, batch processing, PDF reports and
additional rule bundles. Independent totals recomputation needs a specified rounding
policy and its own corpus. Invoice creation remains outside current scope.

## Design constraints

- Core has no Spring, Vaadin or presentation dependencies.
- Use BigDecimal for money and preserve raw source precision; never silently round.
- Validation and display parsing have independent outcomes within the same processing limits.
- Unsupported or incomplete rule checks never yield a valid verdict.
- A PDF container is not proof of PDF/A compliance.
- Profile declarations and selected rule-bundle versions are separate facts.
- Never invent rule meanings or infer payment approval from a valid result.
- No real invoices in fixtures, logs, screenshots or issue reports.
- Runtime processing must not depend on external validation services.
- Local and remote self-hosted deployments require different privacy wording.
