package io.github.einvoicelens.explain;

import io.github.einvoicelens.core.Finding;
import io.github.einvoicelens.core.KositInvoiceValidator;
import io.github.einvoicelens.core.ValidationReport;
import java.util.Locale;

/** A self-contained, text-only report intended to be shared with an invoice supplier. */
public final class HtmlValidationReport {
    private HtmlValidationReport() { }

    public static String render(String fileName, ValidationReport report, Locale locale) {
        String title = switch (report.status()) {
            case VALID -> "Valid";
            case VALID_WITH_WARNINGS -> "Valid with warnings";
            case INVALID -> "Invalid";
            case UNSUPPORTED -> "Not checkable";
            case INCOMPLETE -> "Validation incomplete";
        };
        var html = new StringBuilder("<!doctype html><html lang=\"")
                .append(locale.getLanguage().equals("de") ? "de" : "en").append("\"><head><meta charset=\"utf-8\">")
                .append("<title>Invoice validation report</title><style>")
                .append("body{font:16px system-ui,sans-serif;line-height:1.5;max-width:900px;margin:3rem auto;padding:0 1rem;color:#173c48}h1{margin-bottom:0}article{border-top:1px solid #ccd8dc;padding:1rem 0}.error{color:#a22}.warning{color:#8a5700}.info{color:#245d82}pre{white-space:pre-wrap;background:#f4f7f8;padding:1rem}small{color:#52666d}</style></head><body>")
                .append("<h1>Invoice validation report</h1><p><strong>Verdict: ").append(escape(title)).append("</strong><br>")
                .append("File: ").append(escape(fileName)).append("<br>").append(escape(report.summary())).append("</p>")
                .append("<h2>Validation scope</h2><p>Syntax: ").append(escape(report.syntax())).append("<br>Declared profile: ")
                .append(escape(report.declaredProfile().isBlank() ? "Not declared" : report.declaredProfile())).append("<br>");
        if (report.rulesApplied()) html.append("Rules: ").append(escape(KositInvoiceValidator.RULES)).append("<br>Bundle provenance: ")
                .append(escape(KositInvoiceValidator.BUNDLE_ID)).append("; ").append(escape(KositInvoiceValidator.ENGINE));
        else html.append("No rule verdict was produced.");
        html.append("</p><p><small>This report does not establish PDF/A conformance, authenticity, payment safety, or complete tax/legal compliance.</small></p>");
        html.append("<h2>Findings</h2>");
        if (report.findings().isEmpty()) html.append("<p>No findings were reported.</p>");
        for (Finding finding : report.findings()) {
            boolean official = report.rulesApplied();
            var explanation = official ? ExplanationCatalog.find(finding.ruleId(), KositInvoiceValidator.BUNDLE_ID) : java.util.Optional.<ExplanationCatalog.Explanation>empty();
            html.append("<article><h3 class=\"").append(finding.severity().name().toLowerCase(Locale.ROOT)).append("\">")
                    .append(escape(finding.severity().name())).append(" · ").append(escape(finding.ruleId())).append("</h3>");
            explanation.ifPresent(entry -> html.append("<p>").append(escape(entry.text(locale))).append("</p><p><strong>Suggested action:</strong> ")
                    .append(escape(entry.hint(locale))).append("</p><p><small>Official rule: ").append(escape(entry.artifact()))
                    .append(" · ").append(escape(ExplanationCatalog.BUNDLE_ID)).append(" · <a href=\"").append(escapeAttribute(ExplanationCatalog.SOURCE_URL)).append("\">source</a></small></p>"));
            html.append("<p><strong>").append(official ? "Official rule text" : "Application message")
                    .append("</strong></p><pre>").append(escape(finding.message())).append("</pre>");
            if (!finding.location().isBlank()) html.append("<p><small>XML location: ").append(escape(finding.location())).append("</small></p>");
            html.append("</article>");
        }
        return html.append("</body></html>").toString();
    }

    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private static String escapeAttribute(String value) { return escape(value); }
}
