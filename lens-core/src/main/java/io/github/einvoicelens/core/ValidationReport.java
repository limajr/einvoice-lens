package io.github.einvoicelens.core;

import java.util.List;

public record ValidationReport(ValidationStatus status, String summary, String syntax,
                               String declaredProfile, List<Finding> findings, List<Check> checks,
                               boolean rulesApplied) {
    public static final List<String> CHECK_NAMES = List.of("XML well-formedness", "XML Schema",
            "European rules (EN 16931)", "German rules (XRechnung)");

    public ValidationReport {
        findings = List.copyOf(findings);
        checks = List.copyOf(checks);
    }
    public enum CheckStatus { PASSED, FAILED, WARNINGS, INFORMATION, NOT_RUN }
    public record Check(String name, String outcome, int errors, int warnings, int information) {
        public Check {
            if (errors < 0 || warnings < 0 || information < 0) throw new IllegalArgumentException("Negative finding count");
        }
        public CheckStatus status() {
            if (outcome.equals("Not run")) return CheckStatus.NOT_RUN;
            if (errors > 0) return CheckStatus.FAILED;
            if (warnings > 0) return CheckStatus.WARNINGS;
            if (information > 0) return CheckStatus.INFORMATION;
            return outcome.equals("Passed") ? CheckStatus.PASSED : CheckStatus.FAILED;
        }
    }
    public static ValidationReport stopped(ValidationStatus status, String code, String message) {
        return new ValidationReport(status, message, "Not determined", "", List.of(
                new Finding(code, Finding.Severity.ERROR, message, "")), List.of(), false);
    }
}
