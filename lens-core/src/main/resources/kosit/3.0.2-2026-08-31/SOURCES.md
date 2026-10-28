# Bundled rules

Original, unmodified release archive:
https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/v2026-08-31/xrechnung-3.0.2-validator-configuration-2026-08-31.zip

SHA-256: `2530cd107c414511c5d0462ec10f886910395abfca820db82e83d70bf01221a8`.
The loader verifies this checksum before extracting the public rules to a temporary directory.

Configuration source and Apache-2.0 license:
https://github.com/itplr-kosit/validator-configuration-xrechnung/tree/v2026-08-31

EN16931 1.3.16 source and EUPL-1.2 license:
https://github.com/ConnectingEurope/eInvoicing-EN16931/tree/validation-1.3.16

XRechnung Schematron 2.6.0 source:
https://projekte.kosit.org/xrechnung/xrechnung-schematron/

The archive's README lists its constituent artifacts. UBL 2.1 and UN/CEFACT D16B XSDs
retain OASIS and UN/CEFACT notices, including redistribution conditions, in their headers.
No schema or stylesheet is modified. Original sources for compiled rules remain available
at the links above. LICENSE and LICENSE.EN16931.txt accompany this archive.

The configured archive also contains scenarios outside the application's initial scope.
The application only admits standard XRechnung 3.0 UBL Invoice/CII profiles; it does not
silently validate unknown profiles with a fallback scenario.
