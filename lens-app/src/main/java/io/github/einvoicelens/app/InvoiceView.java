package io.github.einvoicelens.app;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import java.util.concurrent.CompletableFuture;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import io.github.einvoicelens.explain.ExplanationCatalog;
import java.util.ArrayList;
import java.util.Set;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.UploadEvent;
import com.vaadin.flow.server.streams.UploadHandler;
import io.github.einvoicelens.core.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Route("")
@PageTitle("einvoice-lens · Invoice validation")
public class InvoiceView extends Div {
    private UiText ui = new UiText(false);
    private final List<Runnable> staticBindings = new ArrayList<>();
    private boolean collectingStatic = true;
    private String uiLanguage = "English";
    private InvoiceAnalysis currentAnalysis;
    private String currentFileName;
    private boolean sampleFile;
    private String loadingFile;
    private String uploadError;
    private boolean preferValidation;
    private String findingFilter = "All findings";
    private Upload upload;
    private static final int VISIBLE_FINDINGS_LIMIT = 100;
    private final Div results = new Div();
    private final Button clear = new Button("Choose another file");
    private final AtomicLong request = new AtomicLong();
    private String invoiceFormat = "English";
    private final Button validSample = new Button("Try with a sample invoice");
    private final Button invalidSample = new Button("Try an invalid example");

    public InvoiceView(ValidationService service, ObjectProvider<BuildProperties> buildProperties,
                       @Value("${lens.repository-url}") String repositoryUrl) {
        addClassName("lens-app");
        bindButton(clear, "Choose another file");
        bindButton(validSample, "Try with a sample invoice");
        bindButton(invalidSample, "Try an invalid example");
        var wordmark = new Div();
        wordmark.addClassName("wordmark");
        wordmark.add(symbol("el", "brand-mark"), text("einvoice", "brand-name"), text("lens", "brand-accent"));
        var versionPill = text("XRechnung 3.0", "header-tag");
        versionPill.getElement().setAttribute("tabindex", "0");
        var versionTooltip = Tooltip.forComponent(versionPill).withText(KositInvoiceValidator.RULES).withFocusDelay(0);
        staticBindings.add(() -> versionTooltip.setText(ui.t(KositInvoiceValidator.RULES)));
        var language = new Select<String>();
        language.setItems("English", "Deutsch"); language.setValue("English");
        language.addClassName("ui-language");
        staticBindings.add(() -> language.setLabel(ui.t("Language")));
        language.addValueChangeListener(event -> changeLanguage(event.getValue()));
        var headerControls = new Div(language, versionPill); headerControls.addClassName("header-controls");
        var header = new Header(wordmark, headerControls);
        header.addClassName("site-header");
        var hero = new Div(text("A LITTLE CLARITY GOES A LONG WAY", "eyebrow"),
                h1("Check an invoice. Understand the result."),
                paragraph("Read your XRechnung XML and check it against official rules, on your own instance.", "hero-description"));
        hero.addClassName("hero");

        results.addClassName("results-panel");
        results.getElement().setAttribute("aria-live", "polite");
        results.getElement().setAttribute("aria-atomic", "false");
        upload = new Upload(new UploadHandler() {
            @Override public long getFileSizeMax() { return KositInvoiceValidator.MAX_BYTES; }
            @Override public long getRequestSizeMax() { return KositInvoiceValidator.MAX_BYTES + 64 * 1024L; }
            @Override public long getFileCountMax() { return 1; }
            @Override public void handleUploadRequest(UploadEvent event) {
                long id = request.incrementAndGet();
                var ui = event.getUI();
                String name = event.getFileName();
                String displayName = name == null ? "Invoice XML" : name.substring(0, Math.min(name.length(), 200));
                ui.access(() -> {
                    if (request.get() == id) {
                        sampleFile = false;
                        showLoading(displayName);
                    }
                });
                var report = service.analyze(event.getInputStream());
                ui.access(() -> { if (request.get() == id) show(displayName, report); });
            }
        });
        upload.addClassName("invoice-upload");
        upload.setAcceptedFileTypes(".xml");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(KositInvoiceValidator.MAX_BYTES);
        var browse = new Button("Browse files");
        bindButton(browse, "Browse files");
        browse.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        upload.setUploadButton(browse);
        upload.setDropLabel(text("or drop your XML here", "drop-label-text"));
        upload.setDropLabelIcon(symbol("↑", "upload-arrow"));
        upload.addFileRemovedListener(event -> reset());
        upload.addFileRejectedListener(event -> clearWithMessage(event.getErrorMessage()));
        upload.addFailedListener(event -> clearWithMessage("Choose an XML file of at most 5 MiB and try again."));
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        clear.addClassName("clear-button");
        clear.addClickListener(event -> { reset(); upload.clearFileList(); });
        clear.setVisible(false);

        validSample.addClassName("sample-button");
        validSample.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        invalidSample.addClassName("sample-button");
        invalidSample.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        validSample.addClickListener(event -> runSample(service, upload, DemoInvoice.VALID));
        invalidSample.addClickListener(event -> runSample(service, upload, DemoInvoice.INVALID));
        var samples = new Div(paragraph("No invoice at hand?", "small muted"), validSample, invalidSample);
        samples.addClassName("sample-actions");

        var uploadHeading = new Div(text("01", "section-number"), h2("Your invoice"));
        uploadHeading.addClassName("section-heading");
        var formats = new Div(text("UBL", "format-chip"), text("CII", "format-chip"), text("XML · up to 5 MiB", "file-limit"));
        formats.addClassName("format-list");
        var localNote = new Div(symbol("✓", "local-check"),
                paragraph("Your instance. Your data.", "local-title"),
                paragraph("Files are checked on this instance. With remote hosting, they are sent to that server.", "small muted"));
        localNote.addClassName("local-note");
        var sidebar = new Aside(uploadHeading, paragraph("Upload an XRechnung invoice (UBL or CII).", "muted"),
                upload, formats, clear, samples, localNote);
        sidebar.addClassName("upload-panel");
        var workspace = new Main(sidebar, results);
        workspace.addClassName("workspace");
        var build = buildProperties.getIfAvailable();
        String version = build == null ? "development" : "v" + build.getVersion();
        var repository = new Anchor(repositoryUrl, "Open source on GitHub");
        staticBindings.add(() -> repository.setText(ui.t("Open source on GitHub")));
        repository.setTarget("_blank");
        repository.getElement().setAttribute("rel", "noopener noreferrer");
        var footerIdentity = new Div(text("einvoice-lens " + version + " · ", "footer-brand"), repository);
        footerIdentity.addClassName("footer-identity");
        var footer = new Footer(footerIdentity,
                paragraph("Checks structure and business rules—not authenticity, payment safety or complete tax compliance.", "small muted"));
        footer.addClassName("site-footer");
        add(header, hero, workspace, footer);
        collectingStatic = false;
        staticBindings.forEach(Runnable::run);
        updateUploadLanguage();
        showEmpty();
    }

    private void changeLanguage(String language) {
        String previous = uiLanguage;
        uiLanguage = language;
        ui = new UiText(language.equals("Deutsch"));
        if (invoiceFormat.equals(previous)) invoiceFormat = language;
        staticBindings.forEach(Runnable::run);
        updateUploadLanguage();
        getUI().ifPresent(current -> {
            current.setLocale(ui.locale());
            current.getPage().setTitle(ui.t("einvoice-lens · Invoice validation"));
        });
        if (currentAnalysis != null) show(currentFileName, currentAnalysis);
        else if (loadingFile != null) showLoading(loadingFile);
        else if (uploadError != null) renderUploadError(uploadError);
        else showEmpty();
    }

    private void updateUploadLanguage() {
        upload.setI18n(new UploadI18N()
                .setAddFiles(new UploadI18N.AddFiles().setOne(ui.t("Browse files")).setMany(ui.t("Browse files")))
                .setDropFiles(new UploadI18N.DropFiles().setOne(ui.t("or drop your XML here")).setMany(ui.t("or drop your XML here")))
                .setFile(new UploadI18N.File().setRemove(ui.t("Remove")).setRetry(ui.t("Retry")).setStart(ui.t("Start")))
                .setError(new UploadI18N.Error().setTooManyFiles(ui.t("Too many files.")).setFileIsTooBig(ui.t("File is too large.")).setIncorrectFileType(ui.t("Incorrect file type.")))
                .setUploading(new UploadI18N.Uploading()
                        .setStatus(new UploadI18N.Uploading.Status().setConnecting(ui.t("Connecting…")).setStalled(ui.t("Stalled")).setProcessing(ui.t("Processing…")).setHeld(ui.t("Queued")))
                        .setRemainingTime(new UploadI18N.Uploading.RemainingTime().setPrefix(ui.t("Remaining time:") + " ").setUnknown(ui.t("Unknown remaining time")))
                        .setError(new UploadI18N.Uploading.Error().setServerUnavailable(ui.t("Server unavailable")).setUnexpectedServerError(ui.t("Upload failed")).setForbidden(ui.t("Upload forbidden")).setFileTooLarge(ui.t("File is too large.")))));
    }

    private void showLoading(String displayName) {
        currentAnalysis = null; currentFileName = null; uploadError = null; loadingFile = displayName;
        preferValidation = false; findingFilter = "All findings";
        clear.setVisible(true);
        validSample.setEnabled(false);
        invalidSample.setEnabled(false);
        results.removeAll();
        var loading = new Div(symbol("↻", "state-symbol spinning"), h2("Checking your invoice"),
                rawParagraph(displayFileName(displayName), "file-name"), paragraph("Running the bundled validation rules…", "muted"));
        loading.addClassName("empty-state");
        results.add(loading);
    }

    private void runSample(ValidationService service, Upload upload, DemoInvoice sample) {
        upload.clearFileList();
        long id = request.incrementAndGet();
        var ui = getUI().orElseThrow();
        sampleFile = true;
        showLoading(sample.displayName());
        CompletableFuture.supplyAsync(() -> {
            try {
                return service.analyze(sample.open());
            } catch (Exception e) {
                return new InvoiceAnalysis(ValidationReport.stopped(ValidationStatus.INCOMPLETE, "SAMPLE",
                        "The sample could not be checked. Please try uploading an XML file."),
                        InvoiceReading.unavailable("The sample could not be read."));
            }
        }).thenAccept(report -> {
            if (ui.isAttached()) ui.access(() -> { if (request.get() == id) show(sample.displayName(), report); });
        });
    }

    private void enableSamples() {
        validSample.setEnabled(true);
        invalidSample.setEnabled(true);
    }

    private void reset() {
        request.incrementAndGet();
        currentAnalysis = null; currentFileName = null; loadingFile = null; uploadError = null;
        sampleFile = false;
        preferValidation = false; findingFilter = "All findings";
        clear.setVisible(false);
        enableSamples();
        showEmpty();
    }

    private void showEmpty() {
        results.removeAll();
        var heading = new Div(text("02", "section-number"), h2("Invoice & validation"));
        heading.addClassName("section-heading");
        var empty = new Div(symbol("</>", "state-symbol"), h2("Ready when you are"),
                paragraph("Add an invoice to read its details, check its status,\nand review the fields that need attention.", "muted"));
        empty.addClassName("empty-state");
        var descriptions = List.of("Is the XML well formed?", "Does its structure match the schema?",
                "Does it follow European invoice rules?", "Does it meet German invoice rules?");
        var steps = new Div();
        for (int i = 0; i < ValidationReport.CHECK_NAMES.size(); i++) {
            steps.add(step("0" + (i + 1), ValidationReport.CHECK_NAMES.get(i), descriptions.get(i)));
        }
        steps.addClassName("empty-steps");
        results.add(heading, empty, steps);
    }

    private Div step(String number, String title, String description) {
        var step = new Div(text(number, "step-number"), text(title, "step-title"), paragraph(description, "small muted"));
        step.addClassName("empty-step");
        return step;
    }

    private void clearWithMessage(String message) {
        request.incrementAndGet();
        enableSamples();
        currentAnalysis = null; currentFileName = null; loadingFile = null; uploadError = ui.source(message);
        renderUploadError(uploadError);
    }
    private void renderUploadError(String message) {
        results.removeAll();
        results.add(banner("Upload not checked", message, "", "neutral", "!"));
    }

    private void show(String fileName, InvoiceAnalysis analysis) {
        currentAnalysis = analysis; currentFileName = fileName; loadingFile = null; uploadError = null;
        var report = analysis.validation();
        enableSamples();
        results.removeAll();
        clear.setVisible(true);
        String title = switch (report.status()) {
            case VALID -> "Valid";
            case VALID_WITH_WARNINGS -> "Valid with warnings";
            case INVALID -> "Invalid";
            case UNSUPPORTED -> "Unsupported format or profile";
            case INCOMPLETE -> "Validation incomplete";
        };
        String tone = switch (report.status()) {
            case VALID -> "success";
            case VALID_WITH_WARNINGS -> "warning";
            case INVALID -> "error";
            default -> "neutral";
        };
        results.add(banner(title, report.summary(), fileName, tone, report.status() == ValidationStatus.VALID ? "✓" : "!"));
        var invoice = new InvoiceDocumentView(analysis.reading(), invoiceFormat, format -> invoiceFormat = format, ui);
        invoice.setId("invoice-content");
        invoice.getElement().setAttribute("role", "tabpanel");
        invoice.getElement().setAttribute("aria-labelledby", "invoice-tab");
        var validation = new Div();
        validation.setId("validation-content");
        validation.getElement().setAttribute("role", "tabpanel");
        validation.getElement().setAttribute("aria-labelledby", "validation-tab");
        var invoiceTab = new Tab(ui.t("Invoice")); invoiceTab.setId("invoice-tab");
        invoiceTab.getElement().setAttribute("aria-controls", "invoice-content");
        var validationTab = new Tab(ui.t("Validation")); validationTab.setId("validation-tab");
        validationTab.getElement().setAttribute("aria-controls", "validation-content");
        var tabs = new Tabs(invoiceTab, validationTab);
        tabs.addClassName("result-tabs");
        boolean readable = analysis.reading().document().isPresent();
        boolean showInvoice = readable && !preferValidation;
        tabs.setSelectedTab(showInvoice ? invoiceTab : validationTab);
        invoice.setVisible(showInvoice); validation.setVisible(!showInvoice);
        tabs.addSelectedChangeListener(event -> {
            preferValidation = event.getSelectedTab() == validationTab;
            invoice.setVisible(event.getSelectedTab() == invoiceTab);
            validation.setVisible(event.getSelectedTab() == validationTab);
        });
        Consumer<String> navigate = term -> { tabs.setSelectedTab(invoiceTab); invoice.locate(term); };
        results.add(tabs, invoice, validation);
        if (report.rulesApplied()) {
            var checks = new Div();
            checks.addClassName("check-summary");
            for (var check : report.checks()) {
                String toneClass = check.status().name().toLowerCase().replace('_', '-');
                String icon = switch (check.status()) {
                    case PASSED -> "✓"; case FAILED -> "✗"; case WARNINGS -> "!";
                    case INFORMATION -> "·"; case NOT_RUN -> "—";
                };
                var row = new Div(symbol(icon, "check-icon " + toneClass),
                        text(check.name(), "check-name"), text(ui.stage(check), "check-outcome"));
                row.addClassNames("check-row", "stage-" + toneClass);
                checks.add(row);
            }
            validation.add(checks);
        } else validation.add(paragraph("Invoice rule validation was not completed.", "small muted skipped-note"));

        var metadata = new Div();
        metadata.addClassName("validation-metadata");
        metadata.add(paragraph(ui.format("Detected syntax: {0}", ui.t(report.syntax())), "small"));
        if (!report.declaredProfile().isBlank()) metadata.add(rawParagraph(ui.format("Declared profile: {0}", report.declaredProfile()), "small code-path"));
        if (report.rulesApplied()) metadata.add(paragraph(KositInvoiceValidator.ENGINE, "small"), paragraph(KositInvoiceValidator.RULES, "small"));
        var technical = new Details(ui.t("Validation details"), metadata);
        technical.addClassName("technical-details");
        validation.add(technical);

        if (report.findings().isEmpty()) {
            if (report.status() == ValidationStatus.VALID) {
                validation.add(paragraph("All checks passed. No findings to review.", "no-findings"));
            }
            return;
        }
        var findings = report.findings().stream().sorted(Comparator.comparing(Finding::severity)).toList();
        var heading = new Div(h2("Findings"), text(Integer.toString(findings.size()), "count-badge"));
        heading.addClassNames("section-heading", "findings-heading");
        var counts = new Div();
        counts.addClassName("finding-counts");
        for (var severity : Finding.Severity.values()) {
            long count = findings.stream().filter(f -> f.severity() == severity).count();
            if (count > 0) counts.add(text(ui.count(severity, (int) count), "severity severity-" + severity.name().toLowerCase()));
        }
        var filter = new Select<String>();
        filter.setLabel(ui.t("Show findings"));
        filter.setItemLabelGenerator(ui::t);
        filter.setItems("All findings", "Errors only", "Warnings only", "Information only");
        filter.setValue(findingFilter);
        filter.addClassName("finding-filter");
        var toolbar = new Div(counts, filter);
        toolbar.addClassName("findings-toolbar");
        var list = new Div();
        list.addClassName("findings-list");
        filter.addValueChangeListener(event -> {
            findingFilter = event.getValue(); renderFindings(list, findings, event.getValue(), invoice, navigate);
        });
        renderFindings(list, findings, filter.getValue(), invoice, navigate);
        validation.add(heading, toolbar, list);
    }

    private void renderFindings(Div list, List<Finding> findings, String selection,
                                InvoiceDocumentView invoice, Consumer<String> navigate) {
        list.removeAll();
        var selected = findings.stream().filter(f -> switch (selection) {
            case "Errors only" -> f.severity() == Finding.Severity.ERROR;
            case "Warnings only" -> f.severity() == Finding.Severity.WARNING;
            case "Information only" -> f.severity() == Finding.Severity.INFO;
            default -> true;
        }).toList();
        if (selected.isEmpty()) list.add(paragraph("No findings in this category.", "no-findings"));
        for (var finding : selected.stream().limit(VISIBLE_FINDINGS_LIMIT).toList()) {
            var label = switch (finding.severity()) { case ERROR -> "Error"; case WARNING -> "Warning"; case INFO -> "Information"; };
            var heading = new Div(text(label, "severity severity-" + finding.severity().name().toLowerCase()),
                    text(finding.ruleId(), "rule-id"));
            heading.addClassName("finding-heading");
            var article = new Article(heading);
            article.addClassName("finding-row");
            boolean applicationMessage = Set.of("XML", "PARSER", "INPUT-SIZE", "UPLOAD", "VALIDATOR", "BUSY", "TIMEOUT", "CANCELLED", "SAMPLE").contains(finding.ruleId());
            var explanation = ExplanationCatalog.find(finding.ruleId(), currentAnalysis.validation().rulesApplied() ? KositInvoiceValidator.BUNDLE_ID : "");
            if (applicationMessage) article.add(paragraph(finding.message(), "finding-message"));
            else {
                article.add(paragraph(explanation.map(entry -> entry.text(ui.locale()))
                        .orElse(ui.t("No plain-language explanation is available for this rule yet.")), "finding-message"));
                explanation.ifPresent(entry -> {
                    var hint = new Div(text("Suggested action", "hint-label"), rawParagraph(entry.hint(ui.locale()), "finding-hint"));
                    hint.addClassName("explanation-hint"); article.add(hint);
                });
                var officialContent = new Div(rawParagraph(finding.message(), "official-rule-text"));
                explanation.ifPresent(entry -> {
                    var source = new Anchor(ExplanationCatalog.SOURCE_URL, ui.t("Rule source"));
                    source.setTarget("_blank"); source.getElement().setAttribute("rel", "noopener noreferrer");
                    Tooltip.forComponent(source).withText(entry.artifact() + " · " + entry.ruleId() + " · " + ExplanationCatalog.BUNDLE_ID);
                    officialContent.add(source);
                });
                var official = new Details(ui.t("Official rule text"), officialContent);
                official.setOpened(explanation.isEmpty()); official.addClassName("official-rule"); article.add(official);
            }
            var references = new Div(); references.addClassName("finding-fields");
            Pattern.compile("BT-[0-9]+").matcher(finding.message()).results().map(match -> match.group()).distinct()
                    .filter(invoice::canLocate).limit(12).forEach(term -> {
                        var button = new Button(ui.format("View {0}", term), event -> navigate.accept(term));
                        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                        references.add(button);
                    });
            if (references.getComponentCount() > 0) article.add(references);
            if (!finding.location().isBlank()) {
                var location = new Details(ui.t("XML location"), rawParagraph(finding.location(), "code-path small"));
                location.addClassName("finding-location");
                article.add(location);
            }
            list.add(article);
        }
        if (selected.size() > VISIBLE_FINDINGS_LIMIT) list.add(paragraph(
                ui.format("Showing the first {0} of {1} findings in this category.", VISIBLE_FINDINGS_LIMIT, selected.size()), "small muted"));
    }

    private String displayFileName(String name) {
        return sampleFile && name.startsWith("Sample · ") ? ui.format("Sample · {0}", name.substring("Sample · ".length())) : name;
    }
    private void bindButton(Button button, String english) { staticBindings.add(() -> button.setText(ui.t(english))); }
    private H1 h1(String english) {
        var h = new H1(ui.t(english)); if (collectingStatic) staticBindings.add(() -> h.setText(ui.t(english))); return h;
    }
    private H2 h2(String english) {
        var h = new H2(ui.t(english)); if (collectingStatic) staticBindings.add(() -> h.setText(ui.t(english))); return h;
    }
    private static Paragraph rawParagraph(String value, String className) {
        var paragraph = new Paragraph(value); paragraph.addClassNames(className.split(" ")); return paragraph;
    }

    private Div banner(String title, String summary, String fileName, String tone, String glyph) {
        var content = new Div(text("VALIDATION RESULT", "eyebrow"), h2(title));
        if (!fileName.isBlank()) content.add(rawParagraph(displayFileName(fileName), "file-name"));
        content.add(paragraph(summary, "result-summary"));
        var banner = new Div(symbol(glyph, "verdict-icon"), content);
        banner.addClassNames("result-banner", "result-" + tone);
        return banner;
    }

    private Span text(String value, String className) {
        var span = new Span(ui.t(value));
        if (collectingStatic) staticBindings.add(() -> span.setText(ui.t(value)));
        span.addClassNames(className.split(" "));
        return span;
    }

    private Span symbol(String value, String className) {
        var span = text(value, className);
        span.getElement().setAttribute("aria-hidden", "true");
        return span;
    }

    private Paragraph paragraph(String value, String className) {
        var paragraph = new Paragraph(ui.t(value));
        if (collectingStatic) staticBindings.add(() -> paragraph.setText(ui.t(value)));
        paragraph.addClassNames(className.split(" "));
        return paragraph;
    }
}
