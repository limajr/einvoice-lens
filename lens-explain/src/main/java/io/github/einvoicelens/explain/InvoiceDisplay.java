package io.github.einvoicelens.explain;

import io.github.einvoicelens.core.InvoiceDocument.Field;
import io.github.einvoicelens.core.InvoiceDocument.State;
import java.math.*;
import java.text.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Value-preserving presentation. Source text stays in the domain model and in UI tooltips. */
public final class InvoiceDisplay {
    private static final Set<String> MONEY = Set.of("BT-92", "BT-93", "BT-99", "BT-100", "BT-106", "BT-107", "BT-108",
            "BT-109", "BT-110", "BT-111", "BT-112", "BT-113", "BT-114", "BT-115", "BT-116", "BT-117", "BT-131", "BT-136", "BT-137", "BT-141", "BT-142", "BT-146");
    private final Locale locale;
    private final Locale labels;
    public InvoiceDisplay(Locale locale) { this(locale, locale); }
    public InvoiceDisplay(Locale locale, Locale labels) {
        this.locale = locale.getLanguage().equals("de") ? Locale.GERMANY : Locale.UK;
        this.labels = labels.getLanguage().equals("de") ? Locale.GERMANY : Locale.UK;
    }
    public boolean german() { return locale.getLanguage().equals("de"); }
    public String format(Field field) {
        if (field.state() == State.MISSING) return labels.getLanguage().equals("de") ? "Nicht angegeben" : "Not provided";
        if (field.state() == State.UNPARSEABLE) return field.raw() + (field.unit().isBlank() ? "" : " " + field.unit());
        if (field.date().isPresent()) return field.date().orElseThrow().format(DateTimeFormatter.ofPattern(
                german() ? "dd.MM.uuuu" : "dd MMM uuuu", locale));
        String list = switch (field.term()) {
            case "BT-3" -> "1001";
            case "BT-81" -> "4461";
            case "BT-95", "BT-102", "BT-118", "BT-151" -> "5305";
            default -> "";
        };
        if (!list.isEmpty()) return CodeLabels.label(list, field.raw(), labels).map(label -> label + " (" + field.raw() + ")").orElse(field.raw());
        if (isIban(field)) return groupIban(field.raw());
        if (field.number().isPresent()) {
            var number = field.number().orElseThrow();
            if (MONEY.contains(field.term())) return money(number, field.unit());
            String unit = CodeLabels.label("unit", field.unit(), labels).orElse(field.unit());
            return decimal(number) + (unit.isBlank() ? "" : " " + unit);
        }
        return field.raw() + (field.unit().isBlank() ? "" : " " + field.unit());
    }
    public String money(BigDecimal value, String code) {
        try {
            Currency currency = Currency.getInstance(code);
            var format = (DecimalFormat) NumberFormat.getCurrencyInstance(locale);
            format.setCurrency(currency);
            int digits = Math.max(Math.max(0, currency.getDefaultFractionDigits()), Math.max(0, value.stripTrailingZeros().scale()));
            format.setMinimumFractionDigits(Math.max(0, currency.getDefaultFractionDigits()));
            format.setMaximumFractionDigits(digits);
            format.setRoundingMode(RoundingMode.UNNECESSARY);
            return format.format(value);
        } catch (IllegalArgumentException e) { return decimal(value) + (code.isBlank() ? "" : " " + code); }
    }
    private String decimal(BigDecimal value) {
        var format = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(Math.max(0, value.stripTrailingZeros().scale()));
        format.setRoundingMode(RoundingMode.UNNECESSARY);
        return format.format(value);
    }
    public static boolean isIban(Field field) {
        return Set.of("BT-84", "BT-91").contains(field.term()) && field.raw().replace(" ", "").matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");
    }
    public static String copyIban(Field field) { return field.raw().replace(" ", ""); }
    private static String groupIban(String value) {
        String compact = value.replace(" ", "");
        var grouped = new StringBuilder();
        for (int i = 0; i < compact.length(); i++) { if (i > 0 && i % 4 == 0) grouped.append(' '); grouped.append(compact.charAt(i)); }
        return grouped.toString();
    }
}
