package io.github.einvoicelens.core;

/** Outcomes must distinguish an incomplete check from an invalid document. */
public enum ValidationStatus {
    VALID,
    VALID_WITH_WARNINGS,
    INVALID,
    UNSUPPORTED,
    INCOMPLETE
}
