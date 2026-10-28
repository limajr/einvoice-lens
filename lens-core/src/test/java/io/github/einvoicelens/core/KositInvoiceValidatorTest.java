package io.github.einvoicelens.core;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KositInvoiceValidatorTest {
    private KositInvoiceValidator validator;
    @BeforeAll void start() throws Exception { validator = new KositInvoiceValidator(); }
    @AfterAll void stop() throws Exception { validator.close(); }

    private byte[] sample(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream("/samples/" + path)) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }
    private String standard() throws Exception {
        return new String(sample("kosit/business-cases/standard/01.01a-INVOICE_ubl.xml"), StandardCharsets.UTF_8);
    }
    private ValidationReport validate(String xml) { return validator.validate(xml.getBytes(StandardCharsets.UTF_8)); }

    @TestFactory Stream<DynamicTest> pinnedGoldenCorpus() {
        return Stream.of("ubl", "uncefact").flatMap(syntax -> Stream.of(
                new Object[]{"kosit/business-cases/standard/01.01a-INVOICE_" + syntax + ".xml", ValidationStatus.VALID, Set.of("BR-DE-TMP-32:INFO")},
                new Object[]{"kosit/technical-cases/cius/01.05_minimal_test_" + syntax + ".xml", ValidationStatus.VALID, Set.of("BR-DE-TMP-32:INFO")},
                new Object[]{"kosit/technical-cases/cius/01.01_comprehensive_test_" + syntax + ".xml",
                        syntax.equals("ubl") ? ValidationStatus.VALID : ValidationStatus.VALID_WITH_WARNINGS,
                        syntax.equals("ubl") ? Set.of() : Set.of("CII-SR-475:WARNING", "CII-SR-476:WARNING")},
                new Object[]{"derived/missing-bt-10-" + syntax + ".xml", ValidationStatus.INVALID, Set.of("BR-DE-15:ERROR", "BR-DE-TMP-32:INFO")},
                new Object[]{"derived/incorrect-bt-112-" + syntax + ".xml", ValidationStatus.INVALID,
                        Set.of("BR-CO-15:ERROR", "BR-CO-16:ERROR", "BR-DE-TMP-32:INFO")}
        )).map(test -> DynamicTest.dynamicTest((String) test[0], () -> {
            var result = validator.validate(sample((String) test[0]));
            assertEquals(test[1], result.status(), result.toString());
            assertEquals(test[2], result.findings().stream().map(f -> f.ruleId() + ":" + f.severity()).collect(Collectors.toSet()));
            assertTrue(result.rulesApplied());
            assertEquals(4, result.checks().size());
            assertTrue(result.checks().stream().noneMatch(c -> c.outcome().equals("Not run")));
        }));
    }

    @Test void stagesCountTheirOwnErrorsWarningsAndInformation() throws Exception {
        var invalid = validator.validate(sample("derived/missing-bt-10-ubl.xml"));
        var national = invalid.checks().get(3);
        assertEquals(ValidationReport.CheckStatus.FAILED, national.status());
        assertEquals(1, national.errors()); assertEquals(1, national.information());
        assertEquals(ValidationReport.CheckStatus.PASSED, invalid.checks().get(2).status());
        assertEquals(0, invalid.checks().get(2).errors());
        var warnings = validator.validate(sample("kosit/technical-cases/cius/01.01_comprehensive_test_uncefact.xml"));
        assertEquals(ValidationReport.CheckStatus.WARNINGS, warnings.checks().get(2).status());
        assertEquals(2, warnings.checks().get(2).warnings());
        var valid = validator.validate(sample("kosit/business-cases/standard/01.01a-INVOICE_ubl.xml"));
        assertEquals(ValidationReport.CheckStatus.INFORMATION, valid.checks().get(3).status());
        assertEquals(0, valid.checks().get(3).errors());
        var notRun = new ValidationReport.Check("Schema", "Not run", 0, 0, 0);
        assertEquals(ValidationReport.CheckStatus.NOT_RUN, notRun.status());
    }

    @Test void unknownProfileDoesNotFallBackToEuropeanRules() throws Exception {
        var result = validate(standard().replace("xrechnung_3.0", "xrechnung_4.0"));
        assertEquals(ValidationStatus.UNSUPPORTED, result.status());
        assertFalse(result.rulesApplied());
        assertTrue(result.findings().isEmpty());
    }

    @Test void arbitraryXmlIsUnsupported() {
        assertEquals(ValidationStatus.UNSUPPORTED, validate("<invoice/>").status());
    }

    @Test void schemaFailureIsInvalidEvenWhenBusinessRulesAreSkipped() throws Exception {
        var result = validate(standard().replace("<cbc:ID>123456XX</cbc:ID>", ""));
        assertEquals(ValidationStatus.INVALID, result.status(), result.toString());
        assertFalse(result.findings().isEmpty());
    }

    @TestFactory Stream<DynamicTest> rejectUnsafeOrMalformedXmlBeforeKosit() throws Exception {
        String invoice = standard();
        return Stream.of(
                "<broken>",
                "<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><x>&secret;</x>",
                "<!DOCTYPE x SYSTEM 'http://127.0.0.1:9/remote.dtd'><x/>",
                "<!DOCTYPE x [<!ENTITY a 'abc'><!ENTITY b '&a;&a;&a;'>]><x>&b;</x>",
                "<?xml-stylesheet href='http://127.0.0.1:9/style.xsl'?><x/>",
                "<x xmlns:xi='http://www.w3.org/2001/XInclude'><xi:include href='file:///etc/passwd'/></x>",
                invoice.replace("<ubl:Invoice ", "<ubl:Invoice xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='urn:test http://127.0.0.1:9/evil.xsd' "),
                invoice.replace("<ubl:Invoice ", "<ubl:Invoice xml:base='file:///etc/' "),
                "<x>".repeat(65) + "</x>".repeat(65),
                "<x>" + "<y/>".repeat(100_001) + "</x>",
                invoice.replace("<cbc:ID>123456XX</cbc:ID>", "<cbc:CustomizationID>duplicate</cbc:CustomizationID><cbc:ID>123456XX</cbc:ID>")
        ).map(xml -> DynamicTest.dynamicTest("Reject prohibited XML (" + xml.length() + " chars)", () -> {
            var result = validate(xml);
            assertEquals(ValidationStatus.INVALID, result.status(), result.toString());
            assertEquals("XML", result.findings().getFirst().ruleId());
            assertFalse(result.rulesApplied());
        }));
    }

    @Test void encodedDoctypeIsAlsoRejected() {
        var xml = "<?xml version='1.0' encoding='UTF-16'?><!DOCTYPE x [<!ENTITY a 'secret'>]><x>&a;</x>";
        assertEquals(ValidationStatus.INVALID, validator.validate(xml.getBytes(StandardCharsets.UTF_16)).status());
    }

    @Test void sizeLimitIsEnforcedByCore() {
        assertEquals(ValidationStatus.INCOMPLETE, validator.validate(new byte[KositInvoiceValidator.MAX_BYTES + 1]).status());
        assertEquals(ValidationStatus.INCOMPLETE, validator.validate(new byte[0]).status());
    }
}
