# Plain-language explanations

Seven reviewed DE/EN entries apply only to XRechnung configuration `3.0.2-2026-08-31`.
This is a starting catalog, not coverage of every KoSIT rule. Unknown rule IDs and
configuration versions have no invented explanation. Original rule messages remain intact.

Primary source: https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/tag/v2026-08-31
Archive: xrechnung-3.0.2-validator-configuration-2026-08-31.zip
SHA-256: 2530cd107c414511c5d0462ec10f886910395abfca820db82e83d70bf01221a8

Each TSV entry records the exact compiled official-rule artifact and ID. The catalog's
language and hints are project-authored, based on the rule text in that pinned artifact.
Both syntaxes share the listed business-rule meanings. The bundle's European rules are
EN16931 1.3.16; national rules use XRechnung Schematron 2.6.0. Tests confirm each rule
exists in its recorded artifact and that version mismatches fall back safely.

BR-DE-15 requires a non-empty BT-10. The Leitweg-ID is a practical example, not a new
validation requirement or a claim that every buyer reference must be a Leitweg-ID.
BR-DE-TMP-32 is informational in this configuration; its wording remains a recommendation.
Explanations do not authorize payment, establish authenticity or replace official findings.
