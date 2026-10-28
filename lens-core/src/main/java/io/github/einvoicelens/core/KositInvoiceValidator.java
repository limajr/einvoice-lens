package io.github.einvoicelens.core;

import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.InputFactory;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import de.kosit.validationtool.impl.xml.StrictRelativeResolvingStrategy;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import org.w3c.dom.Element;

public final class KositInvoiceValidator implements InvoiceValidator, AutoCloseable {
    public static final int MAX_BYTES = XmlPreflight.MAX_BYTES;
    public static final String BUNDLE_ID = "3.0.2-2026-08-31";
    public static final String ENGINE = "KoSIT Validator 1.6.3";
    public static final String RULES = "XRechnung 3.0.2 · KoSIT configuration of 2026-08-31 · EN 16931 1.3.16";
    private static final String REPORT_NS = "http://www.xoev.de/de/validator/varl/1";
    private final BundledRules rules;
    private final DefaultCheck check;

    public KositInvoiceValidator() throws Exception {
        rules = new BundledRules();
        try {
            var processor = ProcessorProvider.getProcessor();
            var strategy = new StrictRelativeResolvingStrategy() {
                @Override
                public Validator createValidator(Schema schema) {
                    var validator = super.createValidator(schema);
                    try {
                        // Trusted schema imports are resolved at startup; input cannot request any resource.
                        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                        validator.setResourceResolver((type, namespace, publicId, systemId, baseURI) -> {
                            throw new IllegalStateException("Input resource resolution is forbidden");
                        });
                    } catch (org.xml.sax.SAXException e) { throw new IllegalStateException(e); }
                    return validator;
                }
            };
            var config = Configuration.load(rules.directory().resolve("scenarios.xml").toUri(), rules.directory().toUri())
                    .setResolvingStrategy(strategy).build(processor);
            check = new DefaultCheck(processor, config);
        } catch (Exception e) {
            rules.close();
            throw e;
        }
    }

    @Override
    public synchronized ValidationReport validate(byte[] xml) {
        if (xml.length == 0 || xml.length > MAX_BYTES) return ValidationReport.stopped(
                ValidationStatus.INCOMPLETE, "INPUT-SIZE", "Choose a non-empty XML file of at most 5 MiB.");
        XmlPreflight.Detected detected;
        try {
            detected = XmlPreflight.inspect(xml);
        } catch (org.xml.sax.SAXException e) {
            // Do not expose parser messages containing invoice values or external resource addresses.
            return ValidationReport.stopped(ValidationStatus.INVALID, "XML",
                    "XML is malformed or uses a prohibited feature (DTD, external schema hints, processing instructions, or excessive structure).");
        } catch (Exception e) {
            return ValidationReport.stopped(ValidationStatus.INCOMPLETE, "PARSER", "XML inspection could not complete.");
        }
        if (detected.syntax().equals("Unknown") || !detected.profile().equals(XmlPreflight.PROFILE)) {
            return new ValidationReport(ValidationStatus.UNSUPPORTED,
                    "Only standard XRechnung 3.0 UBL Invoice and CII XML are supported. No invoice rules were run.",
                    detected.syntax(), detected.profile(), List.of(), List.of(), false);
        }
        try {
            var result = check.checkInput(InputFactory.read(xml, "invoice.xml"));
            if (!result.isProcessingSuccessful() || result.getReportDocument() == null) {
                return ValidationReport.stopped(ValidationStatus.INCOMPLETE, "VALIDATOR", "Validation could not complete. No valid verdict is available.");
            }
            var document = result.getReportDocument();
            var findings = new ArrayList<Finding>();
            var messages = elements(document.getDocumentElement(), "message");
            for (int i = 0; i < messages.size(); i++) {
                var message = messages.get(i);
                var severity = switch (message.getAttribute("level")) {
                    case "information" -> Finding.Severity.INFO;
                    case "warning" -> Finding.Severity.WARNING;
                    default -> Finding.Severity.ERROR;
                };
                String code = message.getAttribute("code");
                findings.add(new Finding(code.isBlank() ? "XSD" : code, severity,
                        message.getTextContent().strip(), message.getAttribute("xpathLocation")));
            }
            var checks = new ArrayList<ValidationReport.Check>();
            var steps = elements(document.getDocumentElement(), "validationStepResult");
            var ids = List.of("val-xml", "val-xsd", "val-sch.1", "val-sch.2");
            var names = ValidationReport.CHECK_NAMES;
            for (int i = 0; i < ids.size(); i++) {
                String outcome = "Not run";
                int errors = 0, warnings = 0, information = 0;
                for (int j = 0; j < steps.size(); j++) {
                    var step = steps.get(j);
                    if (step.getAttribute("id").equals(ids.get(i))) {
                        outcome = step.getAttribute("valid").equals("true") ? "Passed" : "Findings reported";
                        for (var message : elements(step, "message")) {
                            switch (message.getAttribute("level")) {
                                case "information" -> information++;
                                case "warning" -> warnings++;
                                default -> errors++;
                            }
                        }
                    }
                }
                checks.add(new ValidationReport.Check(names.get(i), outcome, errors, warnings, information));
            }
            boolean allRun = checks.stream().noneMatch(c -> c.outcome().equals("Not run"));
            var status = !result.isAcceptable() ? ValidationStatus.INVALID
                    : !allRun ? ValidationStatus.INCOMPLETE
                    : findings.stream().anyMatch(f -> f.severity() == Finding.Severity.ERROR) ? ValidationStatus.INVALID
                    : findings.stream().anyMatch(f -> f.severity() == Finding.Severity.WARNING) ? ValidationStatus.VALID_WITH_WARNINGS
                    : ValidationStatus.VALID;
            return new ValidationReport(status, switch (status) {
                case VALID -> "Accepted by the bundled rules. Informational findings may still be present.";
                case VALID_WITH_WARNINGS -> "Accepted by the bundled rules, with warnings to review.";
                case INVALID -> "The document was rejected by the bundled rules. Review the findings below.";
                default -> "Some checks did not complete. No valid verdict is available.";
            }, detected.syntax(), detected.profile(), findings, checks, true);
        } catch (RuntimeException e) {
            return ValidationReport.stopped(ValidationStatus.INCOMPLETE, "VALIDATOR", "Validation could not complete. No valid verdict is available.");
        }
    }

    private static List<Element> elements(org.w3c.dom.Node node, String name) {
        var matches = new ArrayList<Element>();
        if (node instanceof Element element && REPORT_NS.equals(element.getNamespaceURI())
                && name.equals(element.getLocalName())) matches.add(element);
        for (var child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            matches.addAll(elements(child, name));
        }
        return matches;
    }

    @Override
    public void close() throws Exception { rules.close(); }
}
