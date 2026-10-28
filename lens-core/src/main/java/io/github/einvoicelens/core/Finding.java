package io.github.einvoicelens.core;

public record Finding(String ruleId, Severity severity, String message, String location) {
    public enum Severity { ERROR, WARNING, INFO }
}
