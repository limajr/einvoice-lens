# einvoice-lens

Read README.md and docs/ROADMAP.md for the public scope and milestone plan.
Java 21, Spring Boot 4.1.2, Vaadin 25.3.4, Maven modules. Project code uses MIT.

- Keep lens-core independent of Spring, Vaadin, lens-app and lens-explain.
- Use BigDecimal for money; preserve source values and precision.
- Validate XML independently of constructing a display model.
- Unsupported or incomplete validation must never yield a valid verdict.
- Read actual APIs before integrating KoSIT, Mustang or other libraries.
- Pin rule bundles and preserve their provenance and redistribution notices.
- Explanations must cite the official rule and applicable version.
- Harden every XML/XSLT path; no runtime external resource resolution.
- Use synthetic or redistributable official fixtures, never real invoice data.
- Test meaningful failure paths and security boundaries as they are implemented.

Commands:
- `./mvnw verify`
- `./mvnw -Pproduction verify`
- `./mvnw install -DskipTests` then `./mvnw -pl lens-app spring-boot:run`
