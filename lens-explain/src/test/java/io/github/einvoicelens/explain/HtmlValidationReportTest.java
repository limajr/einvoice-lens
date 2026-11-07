package io.github.einvoicelens.explain;

import io.github.einvoicelens.core.*;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HtmlValidationReportTest {
    @Test void escapesSupplierControlledValuesAndIncludesRuleProvenance() {
        var report = new ValidationReport(ValidationStatus.INVALID, "Rejected <now>", "CII", "profile", "Rule set", List.of(
                new Finding("BR-DE-15", Finding.Severity.ERROR, "Rule says <fix>", "/x")), List.of(), true);
        String html = HtmlValidationReport.render("<invoice>.xml", "", report, Locale.UK, key -> key);
        assertTrue(html.contains("&lt;invoice&gt;.xml"));
        assertTrue(html.contains("Rule says &lt;fix&gt;"));
        assertTrue(html.contains(KositInvoiceValidator.BUNDLE_ID));
        assertTrue(html.contains("Suggested action:"));
    }
}
