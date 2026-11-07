# Security

This is an early development preview with bounded XML/PDF uploads and local KoSIT validation.
It is not ready for public internet deployment or production invoice processing.

Please do not disclose sensitive invoice data or exploitable vulnerability details in
public issues. Use GitHub's private vulnerability reporting when enabled on the hosting
repository. If unavailable, open an issue requesting a private contact without technical
details or attachments. No supported production release exists yet.

Validation uses a 5 MiB file limit, hardened XML preflight, and PDF extraction that rejects
encrypted or action-bearing PDFs, JavaScript name trees, excess attachments and ambiguous
XML attachments. It does not render PDFs or execute their content. There is no input-driven external
schema resolution, one worker, no queue and a 30-second response deadline. A timed-out
library task may continue until it honors interruption or finishes, retaining the worker
slot; process isolation is not implemented. Do not expose this preview as a public upload
service. Multipart processing may use container-managed temporary files. Invoice values
can appear in the findings retained in the UI session; clear them when finished.
