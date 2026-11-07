package io.github.einvoicelens.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;

/** Extracts an invoice XML attachment only. PDF rendering, JavaScript and PDF/A claims are out of scope. */
final class PdfInvoiceExtractor {
    static final int MAX_EMBEDDED_FILES = 10;

    record Extracted(byte[] xml, String name) { }

    Extracted extract(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) throw new IOException("Encrypted PDF");
            if (document.getDocumentCatalog().getOpenAction() != null || document.getDocumentCatalog().getActions() != null
                    && document.getDocumentCatalog().getActions().getCOSObject().size() > 0) {
                throw new IOException("PDF actions are not accepted");
            }
            var names = document.getDocumentCatalog().getNames();
            if (names == null || names.getEmbeddedFiles() == null) throw new IOException("No embedded files");
            if (names.getJavaScript() != null) throw new IOException("PDF JavaScript is not accepted");
            Map<String, PDComplexFileSpecification> files = names.getEmbeddedFiles().getNames();
            if (files == null || files.isEmpty()) throw new IOException("No embedded files");
            if (files.size() > MAX_EMBEDDED_FILES) throw new IOException("Too many embedded files");
            var candidates = new ArrayList<Extracted>();
            for (var entry : files.entrySet()) {
                PDComplexFileSpecification specification = entry.getValue();
                PDEmbeddedFile embedded = specification.getEmbeddedFileUnicode();
                if (embedded == null) embedded = specification.getEmbeddedFile();
                if (embedded == null) continue;
                String name = specification.getFilename();
                if (name == null || name.isBlank()) name = entry.getKey();
                byte[] bytes;
                try (var input = embedded.createInputStream()) {
                    bytes = input.readNBytes(KositInvoiceValidator.MAX_BYTES + 1);
                }
                if (bytes.length > 0 && bytes.length <= KositInvoiceValidator.MAX_BYTES && looksLikeXml(bytes)) {
                    candidates.add(new Extracted(bytes, name));
                }
            }
            if (candidates.size() != 1) throw new IOException(candidates.isEmpty() ? "No invoice XML attachment" : "Multiple XML attachments");
            return candidates.getFirst();
        }
    }

    private static boolean looksLikeXml(byte[] bytes) {
        for (byte value : bytes) {
            if (!Character.isWhitespace((char) (value & 0xff))) return value == '<';
        }
        return false;
    }
}
