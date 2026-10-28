package io.github.einvoicelens.core;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationServiceTest {
    @Test void oversizedUploadsNeverReachValidator() {
        var calls = new AtomicInteger();
        try (var service = new ValidationService(bytes -> { calls.incrementAndGet(); return null; }, Duration.ofSeconds(2))) {
            var report = service.validate(new ByteArrayInputStream(new byte[KositInvoiceValidator.MAX_BYTES + 1]));
            assertEquals("INPUT-SIZE", report.findings().getFirst().ruleId());
            assertEquals(0, calls.get());
        }
    }

    @Test void deadlineDoesNotPermitMoreWorkersWhenEngineIgnoresInterrupts() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var service = new ValidationService(bytes -> {
            calls.incrementAndGet(); started.countDown();
            while (release.getCount() > 0) {
                try { release.await(); } catch (InterruptedException ignored) { /* simulate stuck library */ }
            }
            return ValidationReport.stopped(ValidationStatus.INCOMPLETE, "TEST", "Test finished");
        }, Duration.ofMillis(200))) {
            var future = CompletableFuture.supplyAsync(() -> service.validate(new ByteArrayInputStream(new byte[]{1})));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals("TIMEOUT", future.get(2, TimeUnit.SECONDS).findings().getFirst().ruleId());
            assertEquals("BUSY", service.validate(new ByteArrayInputStream(new byte[]{1})).findings().getFirst().ruleId());
            assertEquals(1, calls.get());
        } finally { release.countDown(); }
    }

    @Test void engineFailureNeverBecomesValidAndDoesNotExposeExceptionContent() {
        try (var service = new ValidationService(bytes -> { throw new IllegalStateException("private invoice"); }, Duration.ofSeconds(2))) {
            var result = service.validate(new ByteArrayInputStream(new byte[]{1}));
            assertEquals(ValidationStatus.INCOMPLETE, result.status());
            assertFalse(result.toString().contains("private invoice"));
        }
    }
}
