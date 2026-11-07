package io.github.einvoicelens.app;

import io.github.einvoicelens.core.ValidationReport;
import io.github.einvoicelens.core.*;
import io.github.einvoicelens.explain.HtmlValidationReport;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiTextTest {
    @Test void uiAndFieldLabelsAreAvailableInBothLanguages() {
        var en = new UiText(false); var de = new UiText(true);
        assertEquals("Your invoice", en.t("Your invoice"));
        assertEquals("Ihre Rechnung", de.t("Your invoice"));
        assertEquals("Käuferreferenz", de.t("Buyer reference"));
        assertEquals("Offizieller Regeltext", de.t("Official rule text"));
        assertEquals("Nur Fehler", de.t("Errors only"));
        assertTrue(de.t("Add an invoice to read its details, check its status,\nand review the fields that need attention.").startsWith("Fügen Sie"));
        assertEquals("Datei nicht geprüft", de.t("Upload not checked"));
        assertEquals("File is too large.", de.source("Die Datei ist zu groß."));
    }
    @Test void stagesRenderCountsAndPluralForms() {
        var en = new UiText(false); var de = new UiText(true);
        var failed = new ValidationReport.Check("German rules", "Findings reported", 1, 0, 1);
        assertEquals("Failed · 1 error", en.stage(failed));
        assertEquals("Fehlgeschlagen · 1 Fehler", de.stage(failed));
        assertEquals("2 warnings", en.stage(new ValidationReport.Check("European rules", "Findings reported", 0, 2, 0)));
        assertEquals("1 Hinweis", de.stage(new ValidationReport.Check("German rules", "Findings reported", 0, 0, 1)));
    }
    @Test void generatedMessagesTranslateOnlyTheirApplicationOwnedParts() {
        var de = new UiText(true);
        assertEquals("Zahlungsoption 2", de.generated("Payment option 2"));
        assertEquals("Rechnungsdatum (BT-2) konnte nicht interpretiert werden; der Originalwert wird angezeigt.",
                de.generated("Issue date (BT-2) could not be interpreted; the original value is displayed."));
        assertEquals("Käuferreferenz (BT-10) ist mehrfach vorhanden; der erste Wert wird angezeigt.",
                de.generated("Buyer reference (BT-10) occurs more than once; the first value is displayed."));
    }
    @Test void htmlReportUsesTheUiTranslationCatalog() {
        var de = new UiText(true);
        var report = new ValidationReport(ValidationStatus.UNSUPPORTED, "Nicht prüfbar", "CII", "", "", List.of(), List.of(), false);
        String html = HtmlValidationReport.render("rechnung.pdf", "factur-x.xml", report, de.locale(), de::t);
        assertTrue(html.contains("Rechnungsprüfbericht"));
        assertTrue(html.contains("Eingebettetes XML"));
        assertTrue(html.contains("aus PDF"));
    }
}
