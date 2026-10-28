package io.github.einvoicelens.explain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reviewed explanations apply only to the exact bundled rule configuration. */
public final class ExplanationCatalog {
    public static final String BUNDLE_ID = "3.0.2-2026-08-31";
    public static final String SOURCE_URL = "https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/tag/v2026-08-31";
    public record Explanation(String ruleId, List<String> terms, String english, String german,
                              String englishHint, String germanHint, String artifact) {
        public Explanation { terms = List.copyOf(terms); }
        public String text(Locale locale) { return locale.getLanguage().equals("de") ? german : english; }
        public String hint(Locale locale) { return locale.getLanguage().equals("de") ? germanHint : englishHint; }
    }
    private static final Map<String, Explanation> ENTRIES = load();
    private ExplanationCatalog() { }
    public static Optional<Explanation> find(String ruleId, String bundle) {
        return BUNDLE_ID.equals(bundle) ? Optional.ofNullable(ENTRIES.get(ruleId)) : Optional.empty();
    }
    public static Collection<Explanation> entries() { return ENTRIES.values(); }
    private static Map<String, Explanation> load() {
        var entries = new LinkedHashMap<String, Explanation>();
        try (var input = ExplanationCatalog.class.getResourceAsStream("/explanations.tsv")) {
            if (input == null) throw new IllegalStateException("Explanation catalog missing");
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                for (var line : reader.lines().toList()) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    var parts = line.split("\t", -1);
                    if (parts.length != 7 || entries.containsKey(parts[0])) throw new IllegalStateException("Invalid explanation entry");
                    entries.put(parts[0], new Explanation(parts[0], List.of(parts[1].split(",")), parts[2], parts[3], parts[4], parts[5], parts[6]));
                }
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot load explanations", e); }
        return Collections.unmodifiableMap(entries);
    }
}
