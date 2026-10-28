package io.github.einvoicelens.app;

import io.github.einvoicelens.core.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BundledDemoInvoicesTest {
    private KositInvoiceValidator validator;
    private ValidationService service;
    @BeforeAll void start() throws Exception {
        validator = new KositInvoiceValidator();
        service = new ValidationService(validator, Duration.ofSeconds(30));
    }
    @AfterAll void stop() throws Exception { service.close(); validator.close(); }

    @Test void validDemoIsAcceptedByTheActualValidator() throws Exception {
        var analysis = service.analyze(DemoInvoice.VALID.open());
        var report = analysis.validation();
        assertTrue(analysis.reading().document().isPresent());
        assertEquals(ValidationStatus.VALID, report.status());
        assertEquals(ValidationReport.CHECK_NAMES, report.checks().stream().map(ValidationReport.Check::name).toList());
    }

    @Test void invalidDemoReportsMissingBuyerReference() throws Exception {
        var analysis = service.analyze(DemoInvoice.INVALID.open());
        var report = analysis.validation();
        assertEquals(InvoiceDocument.State.MISSING, InvoiceDocument.field(analysis.reading().document().orElseThrow().header(), "BT-10").state());
        assertEquals(ValidationStatus.INVALID, report.status());
        assertTrue(report.findings().stream().anyMatch(f -> f.ruleId().equals("BR-DE-15") && f.severity() == Finding.Severity.ERROR));
    }
}
