package io.github.einvoicelens.core;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;

/** A single worker and no queue bound work even when a timed-out engine ignores interruption. */
public final class ValidationService implements AutoCloseable {
    private final InvoiceValidator validator;
    private final Duration deadline;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("invoice-validation").factory());

    public ValidationService(InvoiceValidator validator, Duration deadline) {
        if (deadline.isNegative() || deadline.isZero()) throw new IllegalArgumentException("Positive deadline required");
        this.validator = validator;
        this.deadline = deadline;
    }

    /** Takes ownership of the input stream, including on rejection or timeout. */
    public ValidationReport validate(InputStream input) {
        return execute(input, this::validateBytes, Function.identity());
    }

    /** Validation and reading have independent results within the same input and work limits. */
    public InvoiceAnalysis analyze(InputStream input) {
        return execute(input, bytes -> analyzeBytes(bytes),
                report -> new InvoiceAnalysis(report, InvoiceReading.unavailable("Invoice reading could not complete.")));
    }

    private InvoiceAnalysis analyzeBytes(byte[] bytes) {
        if (isPdf(bytes)) {
            try {
                var extracted = new PdfInvoiceExtractor().extract(bytes);
                var report = safeValidation(extracted.xml());
                return new InvoiceAnalysis(report, new InvoiceReader().read(extracted.xml()));
            } catch (java.io.IOException e) {
                return new InvoiceAnalysis(ValidationReport.stopped(ValidationStatus.UNSUPPORTED, "PDF",
                        "The PDF could not provide one safe embedded invoice XML. No invoice rules were run."),
                        InvoiceReading.unavailable("No readable embedded invoice XML is available."));
            }
        }
        return new InvoiceAnalysis(safeValidation(bytes), new InvoiceReader().read(bytes));
    }

    private ValidationReport validateBytes(byte[] bytes) {
        if (!isPdf(bytes)) return safeValidation(bytes);
        try { return safeValidation(new PdfInvoiceExtractor().extract(bytes).xml()); }
        catch (java.io.IOException e) {
            return ValidationReport.stopped(ValidationStatus.UNSUPPORTED, "PDF",
                    "The PDF could not provide one safe embedded invoice XML. No invoice rules were run.");
        }
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }

    private ValidationReport safeValidation(byte[] bytes) {
        try { return validator.validate(bytes); }
        catch (RuntimeException e) {
            return ValidationReport.stopped(ValidationStatus.INCOMPLETE, "VALIDATOR", "Validation could not complete.");
        }
    }

    private <T> T execute(InputStream input, Function<byte[], T> operation, Function<ValidationReport, T> failure) {
        try (input) {
            Future<T> future;
            try {
                future = executor.submit(() -> {
                    byte[] bytes = input.readNBytes(KositInvoiceValidator.MAX_BYTES + 1);
                    if (bytes.length == 0 || bytes.length > KositInvoiceValidator.MAX_BYTES) {
                        return failure.apply(ValidationReport.stopped(ValidationStatus.INCOMPLETE, "INPUT-SIZE",
                                "Choose a non-empty XML or PDF file of at most 5 MiB."));
                    }
                    return operation.apply(bytes);
                });
            } catch (RejectedExecutionException e) {
                return failure.apply(ValidationReport.stopped(ValidationStatus.INCOMPLETE, "BUSY",
                        "The validator is busy. Please try again after the current check finishes."));
            }
            try {
                return future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                return failure.apply(ValidationReport.stopped(ValidationStatus.INCOMPLETE, "TIMEOUT",
                        "The validation deadline was reached. No valid verdict is available."));
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                return failure.apply(ValidationReport.stopped(ValidationStatus.INCOMPLETE, "CANCELLED", "Validation was interrupted."));
            } catch (ExecutionException e) {
                return failure.apply(ValidationReport.stopped(ValidationStatus.INCOMPLETE, "VALIDATOR", "Validation could not complete."));
            }
        } catch (java.io.IOException e) {
            return failure.apply(ValidationReport.stopped(ValidationStatus.INCOMPLETE, "UPLOAD", "The uploaded file could not be read."));
        }
    }

    @Override
    public void close() { executor.shutdownNow(); }
}
