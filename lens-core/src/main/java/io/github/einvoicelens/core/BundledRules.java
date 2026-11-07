package io.github.einvoicelens.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;

/** Only public, checksum-pinned standards are extracted. Invoice bytes never enter this directory. */
final class BundledRules implements AutoCloseable {
    private static final String BASE = "/kosit/3.0.2-2026-08-31/";
    private final Path directory;
    private final Path facturxScenarios;

    BundledRules() throws Exception {
        byte[] zip;
        String expected;
        try (var input = getClass().getResourceAsStream(BASE + "configuration.zip");
             var hash = getClass().getResourceAsStream(BASE + "configuration.sha256")) {
            if (input == null || hash == null) throw new IOException("Bundled rules missing");
            zip = input.readAllBytes();
            expected = new String(hash.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII).strip();
        }
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)).equals(expected)) {
            throw new IOException("Bundled rules checksum mismatch");
        }
        directory = Files.createTempDirectory("einvoice-lens-rules-");
        try (var archive = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                var target = directory.resolve(entry.getName()).normalize();
                if (!target.startsWith(directory)) throw new IOException("Unsafe bundled archive path");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(archive, target);
                }
            }
        } catch (Exception e) {
            close();
            throw e;
        }
        facturxScenarios = directory.resolve("facturx-en16931-scenarios.xml");
        Files.writeString(facturxScenarios, FACTURX_EN16931_SCENARIOS, StandardCharsets.UTF_8);
    }

    Path directory() { return directory; }
    Path facturxScenarios() { return facturxScenarios; }

    /* A local dispatcher for the already pinned CII schema and EN 16931 XSLT. It deliberately
       contains no XRechnung CIUS rules: Factur-X EN 16931 is not an XRechnung declaration. */
    private static final String FACTURX_EN16931_SCENARIOS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <scenarios xmlns="http://www.xoev.de/de/validator/framework/1/scenarios" frameworkVersion="1.0.0">
              <name>Factur-X / ZUGFeRD EN 16931 dispatcher</name><author>einvoice-lens</author><date>2026-11-07</date>
              <description><p>Local profile dispatcher using the pinned EN 16931 CII assets.</p></description>
              <scenario><name>Factur-X / ZUGFeRD EN 16931 (CII)</name><description><p>Validates the embedded CII against EN 16931 only.</p></description>
                <namespace prefix="rsm">urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100</namespace>
                <namespace prefix="ram">urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100</namespace>
                <namespace prefix="rep">http://www.xoev.de/de/validator/varl/1</namespace>
                <match>exists(/rsm:CrossIndustryInvoice[rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'factur-x') or contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'zugferd')][contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'en16931')]])</match>
                <validateWithXmlSchema><resource><name>XML Schema for UN/CEFACT CII D16B</name><location>resources/cii/16b/xsd/CrossIndustryInvoice_100pD16B.xsd</location></resource></validateWithXmlSchema>
                <validateWithSchematron><resource><name>Schematron rules for EN16931 (CII)</name><location>resources/cii/16b/xsl/EN16931-CII-validation.xsl</location></resource></validateWithSchematron>
                <createReport><resource><name>Validation report for EN16931 (CII)</name><location>resources/xrechnung-report.xsl</location></resource></createReport>
                <acceptMatch>/rep:report/rep:assessment[1]/rep:accept[1]</acceptMatch>
              </scenario>
              <noScenarioReport><resource><name>Default Report</name><location>resources/default-report.xsl</location></resource></noScenarioReport>
            </scenarios>
            """;

    @Override
    public void close() throws IOException {
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
