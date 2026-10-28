package io.github.einvoicelens.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** A partial display model. Missing or malformed source values do not prevent reading other fields. */
public record InvoiceDocument(String syntax, List<Field> header, Group seller, Group buyer,
                              List<Line> lines, List<Group> vat, List<Field> totals,
                              List<Group> payments, List<Group> adjustments, List<Field> delivery,
                              List<Note> notes, int attachmentCount, List<String> readingIssues) {
    public InvoiceDocument {
        header = List.copyOf(header); lines = List.copyOf(lines); vat = List.copyOf(vat);
        totals = List.copyOf(totals); payments = List.copyOf(payments); adjustments = List.copyOf(adjustments);
        delivery = List.copyOf(delivery); notes = List.copyOf(notes); readingIssues = List.copyOf(readingIssues);
    }
    public record Note(String subject, String text, String raw) { }
    public enum State { PRESENT, MISSING, UNPARSEABLE }
    public record Field(String term, String label, String raw, String unit, State state,
                        Optional<BigDecimal> number, Optional<LocalDate> date) {
        public String display() {
            if (state == State.MISSING) return "Not provided";
            String value = date.map(LocalDate::toString).orElse(raw);
            return value + (unit.isBlank() ? "" : " " + unit);
        }
    }
    public record Group(String title, List<Field> fields) {
        public Group { fields = List.copyOf(fields); }
    }
    public record Line(List<Field> fields, List<Group> adjustments, List<Group> taxes) {
        public Line { fields = List.copyOf(fields); adjustments = List.copyOf(adjustments); taxes = List.copyOf(taxes); }
    }
    public static Field field(List<Field> fields, String term) {
        return fields.stream().filter(f -> f.term().equals(term)).findFirst()
                .orElse(new Field(term, term, "", "", State.MISSING, Optional.empty(), Optional.empty()));
    }
}
