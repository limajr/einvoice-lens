package io.github.einvoicelens.explain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A reviewed display subset; labels never determine whether a code is valid. */
public final class CodeLabels {
    private static final Map<String, String[]> LABELS = load();
    private CodeLabels() { }
    public static Optional<String> label(String list, String code, Locale locale) {
        var label = LABELS.get(list + ":" + code);
        return label == null ? Optional.empty() : Optional.of(label[locale.getLanguage().equals("de") ? 1 : 0]);
    }
    private static Map<String, String[]> load() {
        var labels = new HashMap<String, String[]>();
        try (var input = CodeLabels.class.getResourceAsStream("/code-labels.tsv")) {
            if (input == null) throw new IllegalStateException("Code labels are missing");
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (String line : reader.lines().toList()) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    var parts = line.split("\t", -1);
                    if (parts.length != 4 || labels.put(parts[0] + ":" + parts[1], new String[]{parts[2], parts[3]}) != null)
                        throw new IllegalStateException("Invalid or duplicate code label");
                }
            }
        } catch (IOException e) { throw new IllegalStateException("Code labels could not be loaded", e); }
        return Map.copyOf(labels);
    }
}
