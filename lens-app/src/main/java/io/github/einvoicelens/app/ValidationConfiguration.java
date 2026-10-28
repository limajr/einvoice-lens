package io.github.einvoicelens.app;

import io.github.einvoicelens.core.KositInvoiceValidator;
import io.github.einvoicelens.core.ValidationService;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ValidationConfiguration {
    @Bean(destroyMethod = "close")
    KositInvoiceValidator invoiceValidator() throws Exception { return new KositInvoiceValidator(); }

    @Bean(destroyMethod = "close")
    ValidationService validationService(KositInvoiceValidator validator) {
        return new ValidationService(validator, Duration.ofSeconds(30));
    }
}
