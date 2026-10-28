package io.github.einvoicelens.core;

import java.util.Optional;

public record InvoiceReading(Optional<InvoiceDocument> document, String message) {
    public static InvoiceReading unavailable(String message) { return new InvoiceReading(Optional.empty(), message); }
}
