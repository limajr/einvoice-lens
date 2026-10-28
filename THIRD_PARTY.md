# Third-party components

Original einvoice-lens code is MIT licensed. Dependencies retain their licenses.
This initial inventory is not a substitute for the notices of all resolved artifacts.
Review and package the complete runtime license/notice inventory before distributing v0.1.

| Component | Use | License/source |
| --- | --- | --- |
| Spring Boot / Spring Framework | Runtime framework | [Apache-2.0](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt) |
| Vaadin Flow and open-source components | UI | [Apache-2.0](https://github.com/vaadin/flow/blob/main/LICENSE) |
| ArchUnit | Tests only | [Apache-2.0](https://github.com/TNG/ArchUnit/blob/main/LICENSE) |
| JUnit | Tests only | [EPL-2.0](https://github.com/junit-team/junit-framework/blob/main/LICENSE.md) |
| Maven Wrapper | Build scripts | Apache-2.0 notices in generated scripts |

The selected KoSIT XRechnung test-suite fixtures and derived mutations are Apache-2.0.
Two copies are also bundled for the one-click UI demos, with their license and
[provenance](lens-app/src/main/resources/samples/SOURCES.md);
see [fixture provenance](lens-core/src/test/resources/samples/SOURCES.md) and the
[bundled license](lens-core/src/test/resources/samples/kosit/LICENSE).

## Validation runtime and rules

- KoSIT Validator 1.6.3: Apache-2.0; its unmodified JAR includes LICENSE.validator
  and META-INF/NOTICE. [Source](https://github.com/itplr-kosit/validator/tree/v1.6.3).
- Saxon-HE 12.9: MPL-2.0, unmodified library. Source is available from
  [Maven Central](https://repo.maven.apache.org/maven2/net/sf/saxon/Saxon-HE/12.9/Saxon-HE-12.9-sources.jar).
- JAXB runtime 4.0.9: Eclipse Distribution License 1.0; dependency JARs retain notices.
- XRechnung configuration 3.0.2 / 2026-08-31: distributed unchanged as a ZIP with its
  README and source notices. The configuration's Apache-2.0 license and the EN16931
  EUPL-1.2 license are bundled alongside it. UBL/OASIS and UN/CEFACT schemas retain their
  embedded redistribution notices. These artifacts do not become MIT-licensed.
  See [bundle provenance](lens-core/src/main/resources/kosit/3.0.2-2026-08-31/SOURCES.md).

Mustang is not bundled. Complete the transitive runtime notice inventory before a
published release; this remains a local development preview.

No commercial Vaadin component is intentionally selected.

## Display code labels

lens-explain includes a small set of short code names checked against the dated
OpenPeppol 2025-Q4 publication and project-authored German translations. Full descriptions
and source pages are not redistributed. See [code-list provenance](lens-explain/src/main/resources/CODE_LISTS.md).
