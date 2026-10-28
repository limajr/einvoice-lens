package io.github.einvoicelens.core;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded, namespace-aware inspection before any third-party parser sees the bytes. */
final class XmlPreflight extends DefaultHandler {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final String PROFILE = "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";
    private static final String UBL = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CII = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
    private static final String RAM = "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
    private final List<QName> path = new ArrayList<>();
    private final StringBuilder profile = new StringBuilder();
    private String syntax = "Unknown";
    private int elements;
    private int profiles;
    private boolean readingProfile;

    record Detected(String syntax, String profile) { }

    static Detected inspect(byte[] bytes) throws Exception {
        var factory = SAXParserFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        reader.setProperty("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "64");
        reader.setProperty("http://www.oracle.com/xml/jaxp/properties/elementAttributeLimit", "100");
        var handler = new XmlPreflight();
        reader.setContentHandler(handler);
        reader.setErrorHandler(handler);
        reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("External entities are forbidden"); });
        reader.parse(new InputSource(new ByteArrayInputStream(bytes)));
        return new Detected(handler.syntax, handler.profile.toString().strip());
    }

    @Override
    public void startElement(String uri, String local, String name, Attributes attributes) throws SAXException {
        if (++elements > 100_000 || path.size() >= 64) throw new SAXException("XML structure limit exceeded");
        if (uri.equals("http://www.w3.org/2001/XInclude")) throw new SAXException("XInclude is forbidden");
        for (int i = 0; i < attributes.getLength(); i++) {
            if (attributes.getURI(i).equals(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI)
                    && (attributes.getLocalName(i).equals("schemaLocation")
                    || attributes.getLocalName(i).equals("noNamespaceSchemaLocation"))) {
                throw new SAXException("External schema hints are forbidden");
            }
            if (attributes.getURI(i).equals(XMLConstants.XML_NS_URI) && attributes.getLocalName(i).equals("base")) {
                throw new SAXException("xml:base is forbidden");
            }
        }
        path.add(new QName(uri, local));
        if (path.size() == 1) {
            if (path.getFirst().equals(new QName(UBL, "Invoice"))) syntax = "UBL Invoice";
            if (path.getFirst().equals(new QName(CII, "CrossIndustryInvoice"))) syntax = "CII";
        }
        readingProfile = path.equals(List.of(new QName(UBL, "Invoice"), new QName(CBC, "CustomizationID")))
                || path.equals(List.of(new QName(CII, "CrossIndustryInvoice"),
                new QName(CII, "ExchangedDocumentContext"),
                new QName(RAM, "GuidelineSpecifiedDocumentContextParameter"), new QName(RAM, "ID")));
        if (readingProfile && ++profiles > 1) throw new SAXException("Duplicate profile declaration");
    }

    @Override
    public void characters(char[] chars, int start, int length) throws SAXException {
        if (readingProfile) {
            if (profile.length() + length > 512) throw new SAXException("Profile identifier too long");
            profile.append(chars, start, length);
        }
    }

    @Override
    public void endElement(String uri, String local, String name) {
        path.removeLast();
        readingProfile = false;
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        throw new SAXException("Processing instructions are forbidden");
    }

    @Override
    public void error(SAXParseException exception) throws SAXException { throw exception; }
    @Override
    public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
}
