package io.github.einvoicelens.core;

@FunctionalInterface
public interface InvoiceValidator {
    ValidationReport validate(byte[] xml);
}
