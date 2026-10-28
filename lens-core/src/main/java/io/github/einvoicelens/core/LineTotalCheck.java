package io.github.einvoicelens.core;

import java.math.BigDecimal;
import java.util.Optional;
import static io.github.einvoicelens.core.InvoiceDocument.*;

/** A display-only exact comparison of BT-131's sum with BT-106; never alters validation or source values. */
public record LineTotalCheck(Status status, Optional<BigDecimal> sum, Optional<BigDecimal> stated,
                             String currency, String reason) {
    public enum Status { MATCH, MISMATCH, UNAVAILABLE }

    public static LineTotalCheck of(InvoiceDocument invoice) {
        String currency = field(invoice.header(), "BT-5").raw();
        var subtotal = field(invoice.totals(), "BT-106");
        if (invoice.lines().isEmpty() || currency.isBlank() || subtotal.number().isEmpty()
                || !subtotal.unit().equals(currency) || invoice.readingIssues().stream().anyMatch(
                issue -> issue.contains("BT-131") || issue.contains("BT-106"))) {
            return unavailable(currency, "A reliable line subtotal comparison needs unambiguous amounts and a known currency.");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (var line : invoice.lines()) {
            var amount = field(line.fields(), "BT-131");
            if (amount.number().isEmpty() || !amount.unit().equals(currency)) {
                return unavailable(currency, "One or more line net amounts are missing, malformed, or use another currency.");
            }
            sum = sum.add(amount.number().orElseThrow());
        }
        var stated = subtotal.number().orElseThrow();
        return new LineTotalCheck(sum.compareTo(stated) == 0 ? Status.MATCH : Status.MISMATCH,
                Optional.of(sum), Optional.of(stated), currency,
                "Exact sum of line net amounts (BT-131) compared with the stated line subtotal (BT-106). No rounding or correction.");
    }
    private static LineTotalCheck unavailable(String currency, String reason) {
        return new LineTotalCheck(Status.UNAVAILABLE, Optional.empty(), Optional.empty(), currency, reason);
    }
}
