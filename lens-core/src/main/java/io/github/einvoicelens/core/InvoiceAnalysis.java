package io.github.einvoicelens.core;

/** embeddedFileName is set only when the input was a PDF container. */
public record InvoiceAnalysis(ValidationReport validation, InvoiceReading reading, String embeddedFileName) {
    public InvoiceAnalysis {
        embeddedFileName = embeddedFileName == null ? "" : embeddedFileName;
    }
}
