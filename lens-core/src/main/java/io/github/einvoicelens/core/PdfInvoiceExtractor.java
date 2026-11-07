package io.github.einvoicelens.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.io.RandomAccessReadWriteBuffer;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDDestinationOrAction;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;

/** Extracts an invoice XML attachment only. PDF rendering, JavaScript and PDF/A claims are out of scope. */
final class PdfInvoiceExtractor {
    static final int MAX_EMBEDDED_FILES = 10;
    private static final int MAX_PDF_CACHE_BYTES = 10 * 1024 * 1024;

    record Extracted(byte[] xml, String name) { }

    Extracted extract(byte[] pdf) throws IOException {
        // The outer upload bound is 5 MiB. Keep PDFBox's parsing cache in memory and never permit
        // it to spill untrusted input to a temporary file.
        try (var source = new RandomAccessReadBuffer(pdf);
             PDDocument document = Loader.loadPDF(source, "", null, null,
                     () -> new BoundedStreamCache(MAX_PDF_CACHE_BYTES))) {
            if (document.isEncrypted()) throw new IOException("Encrypted PDF");
            if (isJavaScript(document.getDocumentCatalog().getOpenAction()) || hasJavaScriptCatalogAction(document))
                throw new IOException("PDF JavaScript is not accepted");
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

    private static boolean isJavaScript(PDDestinationOrAction action) {
        return action instanceof PDAction pdfAction && "JavaScript".equals(pdfAction.getSubType());
    }

    private static boolean hasJavaScriptCatalogAction(PDDocument document) throws IOException {
        var actions = document.getDocumentCatalog().getActions();
        if (actions == null) return false;
        return isJavaScript(actions.getWC()) || isJavaScript(actions.getWS()) || isJavaScript(actions.getDS())
                || isJavaScript(actions.getWP()) || isJavaScript(actions.getDP());
    }

    private static boolean looksLikeXml(byte[] bytes) {
        for (byte value : bytes) {
            if (!Character.isWhitespace((char) (value & 0xff))) return value == '<';
        }
        return false;
    }

    /** Caps PDFBox's decoded object-stream cache independently of the compressed upload bound. */
    private static final class BoundedRandomAccessBuffer extends RandomAccessReadWriteBuffer {
        private final long limit;
        BoundedRandomAccessBuffer(long limit) { this.limit = limit; }
        @Override public void write(int value) throws IOException { check(1); super.write(value); }
        @Override public void write(byte[] bytes) throws IOException { check(bytes.length); super.write(bytes); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException { check(length); super.write(bytes, offset, length); }
        private void check(int bytes) throws IOException {
            if (length() > limit - bytes) throw new IOException("PDF decoded-data limit exceeded");
        }
    }

    private static final class BoundedStreamCache implements RandomAccessStreamCache {
        private final long limit;
        BoundedStreamCache(long limit) { this.limit = limit; }
        @Override public RandomAccess createBuffer() { return new BoundedRandomAccessBuffer(limit); }
        @Override public void close() { }
    }
}
