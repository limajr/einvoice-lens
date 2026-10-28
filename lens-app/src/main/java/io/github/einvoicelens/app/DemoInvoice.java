package io.github.einvoicelens.app;

import java.io.IOException;
import java.io.InputStream;

/** Public, pinned KoSIT examples; validated through the normal service, never canned results. */
enum DemoInvoice {
    VALID("Sample · valid-xrechnung-ubl.xml", "/samples/valid-xrechnung-ubl.xml"),
    INVALID("Sample · missing-buyer-reference-ubl.xml", "/samples/invalid-missing-buyer-reference-ubl.xml");

    private final String displayName;
    private final String resource;

    DemoInvoice(String displayName, String resource) {
        this.displayName = displayName;
        this.resource = resource;
    }

    String displayName() { return displayName; }

    InputStream open() throws IOException {
        var input = DemoInvoice.class.getResourceAsStream(resource);
        if (input == null) throw new IOException("Bundled example is unavailable");
        return input;
    }
}
