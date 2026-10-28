package io.github.einvoicelens.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;

/** Only public, checksum-pinned standards are extracted. Invoice bytes never enter this directory. */
final class BundledRules implements AutoCloseable {
    private static final String BASE = "/kosit/3.0.2-2026-08-31/";
    private final Path directory;

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
    }

    Path directory() { return directory; }

    @Override
    public void close() throws IOException {
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
