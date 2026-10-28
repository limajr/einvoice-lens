package io.github.einvoicelens.explain;

import java.util.Locale;
import java.util.zip.ZipInputStream;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplanationCatalogTest {
    @Test void entriesReferenceRulesThatExistInThePinnedArtifacts() throws Exception {
        var artifacts = new HashMap<String, String>();
        try (var input = getClass().getResourceAsStream("/kosit/3.0.2-2026-08-31/configuration.zip")) {
            assertNotNull(input);
            try (var zip = new ZipInputStream(input)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.getName().endsWith("validation.xsl")) artifacts.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        assertEquals(7, ExplanationCatalog.entries().size());
        for (var entry : ExplanationCatalog.entries()) {
            assertTrue(artifacts.containsKey(entry.artifact()), entry.artifact());
            assertTrue(artifacts.get(entry.artifact()).contains("id=\"" + entry.ruleId() + "\""), entry.ruleId());
            assertFalse(entry.text(Locale.UK).isBlank()); assertFalse(entry.text(Locale.GERMANY).isBlank());
            assertFalse(entry.hint(Locale.UK).isBlank()); assertFalse(entry.terms().isEmpty());
        }
    }
    @Test void missingBuyerReferenceHasBilingualActionableExplanation() {
        var entry = ExplanationCatalog.find("BR-DE-15", ExplanationCatalog.BUNDLE_ID).orElseThrow();
        assertTrue(entry.text(Locale.UK).contains("buyer reference (BT-10)"));
        assertTrue(entry.text(Locale.GERMANY).contains("Käuferreferenz (BT-10)"));
        assertTrue(entry.hint(Locale.UK).contains("issuer"));
        assertTrue(entry.hint(Locale.UK).contains("commonly"));
    }
    @Test void unknownRulesAndOtherBundleVersionsHaveNoGuessedExplanation() {
        assertTrue(ExplanationCatalog.find("UNKNOWN-RULE", ExplanationCatalog.BUNDLE_ID).isEmpty());
        assertTrue(ExplanationCatalog.find("BR-DE-15", "4.0").isEmpty());
        assertTrue(ExplanationCatalog.find("BR-DE-15", "").isEmpty());
    }
}
