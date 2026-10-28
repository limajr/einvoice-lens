package io.github.einvoicelens.explain;

import io.github.einvoicelens.core.InvoiceDocument.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvoiceDisplayTest {
    private final InvoiceDisplay en = new InvoiceDisplay(Locale.UK);
    private final InvoiceDisplay de = new InvoiceDisplay(Locale.GERMANY);
    private static String spaces(String text) { return text.replace('\u00a0', ' ').replace('\u202f', ' '); }
    private static Field amount(String raw) { return new Field("BT-115", "Amount due", raw, "EUR", State.PRESENT, Optional.of(new BigDecimal(raw)), Optional.empty()); }
    private static Field code(String term, String raw, String unit) { return new Field(term, "Code", raw, unit, State.PRESENT, Optional.empty(), Optional.empty()); }

    @Test void currencyFormattingPadsWithoutChangingValue() {
        var source = amount("336.9");
        assertEquals("€336.90", en.format(source));
        assertEquals("336,90 €", spaces(de.format(source)));
        assertEquals("336.9", source.raw()); assertEquals(new BigDecimal("336.9"), source.number().orElseThrow());
    }
    @Test void meaningfulExtraDecimalsAreNeverRounded() {
        assertEquals("€1.234", en.format(amount("1.2340")));
        assertEquals("1,234 €", spaces(de.format(amount("1.2340"))));
        assertEquals("-€0.001", en.format(amount("-0.001")));
        assertEquals("€9,007,199,254,740,993.01", en.format(amount("9007199254740993.01")));
    }
    @Test void datesUseUnambiguousLocaleFormats() {
        var date = new Field("BT-2", "Issue date", "20160404", "", State.PRESENT, Optional.empty(), Optional.of(LocalDate.of(2016,4,4)));
        assertEquals("04 Apr 2016", en.format(date)); assertEquals("04.04.2016", de.format(date));
        assertEquals("20160404", date.raw());
    }
    @Test void verifiedCodesHaveLabelsAndKeepTheirCodes() {
        assertEquals("Commercial invoice (380)", en.format(code("BT-3", "380", "")));
        assertEquals("Handelsrechnung (380)", de.format(code("BT-3", "380", "")));
        assertEquals("SEPA credit transfer (58)", en.format(code("BT-81", "58", "")));
        assertEquals("SEPA-Überweisung (58)", de.format(code("BT-81", "58", "")));
        assertEquals("Standard rate (S)", en.format(code("BT-118", "S", "")));
        assertEquals("9999", en.format(code("BT-81", "9999", "")));
    }
    @Test void unitsAreNamedWithoutGuessingUnknownCodes() {
        var quantity = new Field("BT-129", "Quantity", "1", "XPP", State.PRESENT, Optional.of(BigDecimal.ONE), Optional.empty());
        assertEquals("1 piece", en.format(quantity)); assertEquals("1 Stück", de.format(quantity));
        var unknown = new Field("BT-129", "Quantity", "1", "XYZ", State.PRESENT, Optional.of(BigDecimal.ONE), Optional.empty());
        assertEquals("1 XYZ", en.format(unknown));
        assertTrue(CodeLabels.label("unit", "C62", Locale.UK).isPresent());
    }
    @Test void ibanGroupingDoesNotChangeTheCopiedIdentifier() {
        var account = code("BT-84", "DE79000000001234567890", "");
        assertEquals("DE79 0000 0000 1234 5678 90", en.format(account));
        assertEquals("DE79000000001234567890", InvoiceDisplay.copyIban(account));
        assertFalse(InvoiceDisplay.isIban(code("BT-84", "my-account", "")));
        assertEquals("my-account", en.format(code("BT-84", "my-account", "")));
    }
    @Test void labelLanguageAndNumberFormatCanBeSelectedIndependently() {
        var mixed = new InvoiceDisplay(Locale.UK, Locale.GERMANY);
        assertEquals("€336.90", mixed.format(amount("336.9")));
        assertEquals("Handelsrechnung (380)", mixed.format(code("BT-3", "380", "")));
    }
    @Test void missingAndUnparseableValuesAreNotRepaired() {
        var missing = new Field("BT-2", "Date", "", "", State.MISSING, Optional.empty(), Optional.empty());
        var broken = new Field("BT-115", "Amount", "invalid", "EUR", State.UNPARSEABLE, Optional.empty(), Optional.empty());
        assertEquals("Not provided", en.format(missing)); assertEquals("Nicht angegeben", de.format(missing));
        assertEquals("invalid EUR", de.format(broken));
        assertEquals("1.234 INVALID", en.money(new BigDecimal("1.234"), "INVALID"));
    }
}
