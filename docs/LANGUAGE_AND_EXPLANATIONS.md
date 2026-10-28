# Language, explanations and check status

The header Language / Sprache switch supports English and German across application
copy, viewer labels, upload states, summary messages, hints and tooltips. Endonyms
English and Deutsch remain constant in the selectors. Invoice values, notes, filenames,
XML paths and original official messages are source data and are not translated.
The Sample / Beispiel prefix is application copy; the sample filename stays unchanged.

Display format is independent from label language. Initially it follows language changes;
a manually selected differing format remains selected. Preferences belong to the current
UI session, with no account, cookies or server-side preferences database. Changing
language preserves the result, selected tab and finding filter, and does not revalidate.

UiText translates only application-owned keys and structured reader notices. Source
invoice content is rendered with raw text components. Core validation objects remain
framework-independent and retain original messages. The German UI source-key catalog is
`lens-app/src/main/resources/ui-de.tsv`; number/date formatting and display code labels
remain in lens-explain.

## Initial rule catalog

The seven entries are BR-DE-15, BR-CO-10, BR-CO-15, BR-CO-16, BR-DE-TMP-32,
CII-SR-475 and CII-SR-476. Each has a reviewed description, suggested action, business
terms and an exact source artifact. The catalog only applies to bundle
`3.0.2-2026-08-31`. The unknown-rule/version fallback is explicit, with the original text
expanded. Internal upload/parser/timeout messages are not presented as official rules.

See `lens-explain/src/main/resources/EXPLANATIONS.md` and explanations.tsv for provenance.
Grow the catalog by reviewing the pinned official rule text, recording its source,
checking both languages, testing version fallback and adding a targeted fixture where
practical. Do not infer legal compliance or a correction that the rule does not establish.

## Stage status

The adapter counts messages within each validationStepResult. It never assigns all
report findings to a single stage or treats a valid=false flag alone as proof of an error:
KoSIT may set that flag when informational findings exist.

- Errors: FAILED, red cross, error count.
- Warnings without errors: WARNINGS, amber marker and warning count.
- Information without errors/warnings: INFORMATION, grey dot and information count.
- No messages with a passed step: PASSED, green check.
- Absent step: NOT_RUN, dash. An unexplained failed flag stays FAILED, never green.

The original acceptance recommendation still determines the invoice verdict. Stage
presentation and plain-language explanations do not change validator severities,
acceptance or raw messages. The header tooltip identifies the actual rule bundle in
both languages.
