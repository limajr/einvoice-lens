package io.github.einvoicelens.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.filespecification.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdfInvoiceExtractorTest {
    @Test void extractsTheOnlyEmbeddedXmlWithoutOpeningItAsAPdfAction() throws Exception {
        byte[] invoice = "<invoice/>".getBytes(StandardCharsets.UTF_8);
        var extracted = new PdfInvoiceExtractor().extract(pdf(Map.of("factur-x.xml", invoice)));
        assertArrayEquals(invoice, extracted.xml());
        assertEquals("factur-x.xml", extracted.name());
    }

    @Test void refusesAmbiguousXmlAttachments() throws Exception {
        assertThrows(IOException.class, () -> new PdfInvoiceExtractor().extract(pdf(Map.of(
                "invoice.xml", "<invoice/>".getBytes(StandardCharsets.UTF_8),
                "other.xml", "<invoice/>".getBytes(StandardCharsets.UTF_8)))));
    }

    private static byte[] pdf(Map<String, byte[]> attachments) throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var files = new PDEmbeddedFilesNameTreeNode();
            var entries = new java.util.HashMap<String, PDComplexFileSpecification>();
            for (var attachment : attachments.entrySet()) {
                var specification = new PDComplexFileSpecification();
                specification.setFile(attachment.getKey());
                var embedded = new PDEmbeddedFile(document, new ByteArrayInputStream(attachment.getValue()));
                embedded.setSize(attachment.getValue().length);
                specification.setEmbeddedFile(embedded);
                entries.put(attachment.getKey(), specification);
            }
            files.setNames(entries);
            var names = new PDDocumentNameDictionary(document.getDocumentCatalog());
            names.setEmbeddedFiles(files);
            document.getDocumentCatalog().setNames(names);
            document.save(output);
            return output.toByteArray();
        }
    }
}
