# Changelog

## v0.2.0 — 2026-10-31

First published release.

### Read an invoice alongside its validation result

- New Invoice and Validation tabs. Readable invoices open by default when parsing succeeds.
- Framework-independent, namespace-aware UBL Invoice and CII readers for the main invoice
  fields: parties, dates, references, line items, VAT, allowances/charges, supplied totals,
  payment instructions, payment terms, invoicing periods and notes.
- Tolerant display values: missing fields remain visible where important; malformed dates
  and amounts keep their original text and are flagged for review.
- Monetary values preserve source precision. Totals are displayed as supplied and never
  recomputed or repaired. Price base quantities, repeated payment options and accounting
  currency VAT are retained.
- Findings can link to a displayed business term when it has one unambiguous field location.
- The same size, XML-hardening, concurrency and deadline limits cover validation and reading.
- Valid and invalid one-click samples now exercise the complete reading/validation path.

### More natural invoice presentation

- English/German display formats for amounts and dates, preserving numerical value and
  extra meaningful decimals. Raw XML values and BT codes remain available on hover.
- Source-documented DE/EN display labels for common document, payment, VAT and unit codes
  in lens-explain, with raw-code fallbacks. This is a display subset, not a new validator.
- Grouped IBANs with an unspaced copy action, note subject badges, duplicate item-text
  suppression, and a separate exact line-net subtotal comparison.
- Display format is retained across invoices in the UI session and can be selected
  independently from UI/label language.

### Explanations, stage status and language

- Initial source-linked English/German catalog for seven rules, pinned to the exact
  XRechnung 3.0.2 / 2026-08-31 configuration. Findings lead with a short explanation and
  suggested action; the original message stays below as expandable official rule text.
- Unknown rules and other bundle versions use an explicit fallback rather than a guessed
  explanation. Application processing failures are distinguished from official rules.
- Per-stage error, warning and information counts. Errors display a red cross and a
  failed count; warnings display amber; only information uses the grey dot. No stage
  counts are inferred from unrelated findings elsewhere in the report.
- Header language switch translates the application, reader labels, summaries, upload
  controls, hints and tooltips while keeping the current result, selected tab and filter.
  Invoice source values and official messages remain unchanged.
- Exact rule-bundle tooltip remains available with both languages, including keyboard focus.

### Scope

This is the readable XML milestone with an initial explanation catalog and DE/EN UI.
Expanding the catalog, PDF containers, attachment handling, report export and Docker
distribution remain later work. The reader
covers selected business terms rather than a complete lossless representation of all XML.
Full official-corpus coverage, process isolation and a published-distribution notice
inventory remain follow-up work.
