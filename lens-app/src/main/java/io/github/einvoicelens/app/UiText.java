package io.github.einvoicelens.app;

import io.github.einvoicelens.core.Finding;
import io.github.einvoicelens.core.ValidationReport;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;

/** Only application-owned source keys are translated; source invoice data stays untouched. */
final class UiText {
    private static final Map<String, String> DE = load();
    private final boolean german;
    UiText(boolean german) { this.german = german; }
    boolean german() { return german; }
    Locale locale() { return german ? Locale.GERMANY : Locale.UK; }
    String t(String english) { return german ? DE.getOrDefault(english, english) : english; }
    String source(String translated) {
        return german ? DE.entrySet().stream().filter(entry -> entry.getValue().equals(translated))
                .map(Map.Entry::getKey).findFirst().orElse(translated) : translated;
    }
    String format(String template, Object... args) { return new MessageFormat(t(template), locale()).format(args); }
    String count(Finding.Severity severity, int count) {
        return format(switch (severity) {
            case ERROR -> count == 1 ? "{0} error" : "{0} errors";
            case WARNING -> count == 1 ? "{0} warning" : "{0} warnings";
            case INFO -> count == 1 ? "{0} informational finding" : "{0} informational findings";
        }, count);
    }
    String stage(ValidationReport.Check check) {
        return switch (check.status()) {
            case PASSED -> t("Passed");
            case NOT_RUN -> t("Not run");
            case FAILED -> t("Failed") + (check.errors() == 0 ? "" : " · " + count(Finding.Severity.ERROR, check.errors()));
            case WARNINGS -> count(Finding.Severity.WARNING, check.warnings());
            case INFORMATION -> count(Finding.Severity.INFO, check.information());
        };
    }
    String generated(String english) {
        if (!german) return english;
        if (DE.containsKey(english)) return t(english);
        var payment = Pattern.compile("^Payment option ([0-9]+)$").matcher(english);
        if (payment.matches()) return format("Payment option {0}", payment.group(1));
        var adjustment = Pattern.compile("^Adjustment [(]unknown indicator: (.*)[)]$", Pattern.DOTALL).matcher(english);
        if (adjustment.matches()) return format("Adjustment (unknown indicator: {0})", adjustment.group(1));
        for (String suffix : List.of(" could not be interpreted; the original value is displayed.", " occurs more than once; the first value is displayed.")) {
            if (!english.endsWith(suffix)) continue;
            String head = english.substring(0, english.length() - suffix.length());
            int term = head.lastIndexOf(" (");
            if (term > 0 && head.endsWith(")")) return format("{0} ({1})" + suffix, t(head.substring(0,term)), head.substring(term+2, head.length()-1));
        }
        return english;
    }
    private static Map<String, String> load() {
        var result = new HashMap<String, String>();
        try (var stream = UiText.class.getResourceAsStream("/ui-de.tsv")) {
            if (stream == null) throw new IllegalStateException("UI translations missing");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                for (var line : reader.lines().toList()) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    var parts = line.split("\t", -1);
                    if (parts.length != 2 || result.put(parts[0].replace("\\n", "\n"), parts[1].replace("\\n", "\n")) != null)
                        throw new IllegalStateException("Invalid UI translation");
                }
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot load UI translations", e); }
        return Map.copyOf(result);
    }
}
