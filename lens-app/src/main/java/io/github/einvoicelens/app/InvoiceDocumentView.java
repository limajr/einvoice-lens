package io.github.einvoicelens.app;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.select.Select;
import io.github.einvoicelens.core.LineTotalCheck;
import io.github.einvoicelens.explain.InvoiceDisplay;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.shared.Tooltip;
import io.github.einvoicelens.core.InvoiceDocument;
import io.github.einvoicelens.core.InvoiceReading;
import java.util.*;
import static io.github.einvoicelens.core.InvoiceDocument.*;

/** Text-only rendering: invoice content is never interpreted as HTML or a link. */
final class InvoiceDocumentView extends Div {
    private static final Set<String> ESSENTIAL = Set.of("BT-1", "BT-2", "BT-3", "BT-5", "BT-9", "BT-10",
            "BT-27", "BT-44", "BT-106", "BT-109", "BT-110", "BT-112", "BT-115");
    private final Map<String, List<Component>> locations = new HashMap<>();

    private final InvoiceReading reading;
    private final UiText ui;
    private final Div body = new Div();
    private InvoiceDisplay display = new InvoiceDisplay(Locale.UK);

    InvoiceDocumentView(InvoiceReading reading, String preferredFormat, java.util.function.Consumer<String> rememberFormat, UiText ui) {
        this.reading = reading;
        this.ui = ui;
        display = new InvoiceDisplay(preferredFormat.equals("Deutsch") ? Locale.GERMANY : Locale.UK, ui.locale());
        addClassName("invoice-document");
        var format = new Select<String>();
        format.setLabel(ui.t("Display format"));
        format.setItems("English", "Deutsch");
        format.setValue(preferredFormat);
        format.addClassName("invoice-format");
        Tooltip.forComponent(format).withText(ui.t("Formats amounts and dates; the language selector controls labels."));
        format.addValueChangeListener(event -> {
            display = new InvoiceDisplay(event.getValue().equals("Deutsch") ? Locale.GERMANY : Locale.UK, ui.locale());
            rememberFormat.accept(event.getValue());
            render();
        });
        var controls = new Div(format); controls.addClassName("invoice-display-controls");
        add(controls, body);
        render();
    }

    private void render() {
        body.removeAll();
        locations.clear();
        if (reading.document().isEmpty()) {
            body.add(new H2(ui.t("Invoice view unavailable")), new Paragraph(ui.t(reading.message())));
            return;
        }
        var invoice = reading.document().orElseThrow();
        var invoiceNumber = new H2(display.format(field(invoice.header(), "BT-1")));
        Tooltip.forComponent(invoiceNumber).withText(sourceTooltip(field(invoice.header(), "BT-1")));
        var title = new Div(new Span(ui.t("INVOICE")), invoiceNumber,
                new Paragraph(ui.format("Values from the XML · {0}", ui.t(invoice.syntax()))));
        title.addClassName("invoice-title");
        var dueValue = new H2(display.format(field(invoice.totals(), "BT-115")));
        Tooltip.forComponent(dueValue).withText(sourceTooltip(field(invoice.totals(), "BT-115")));
        var due = new Div(new Span(ui.t("AMOUNT DUE · BT-115")), dueValue,
                new Paragraph(ui.t("As stated in the invoice")));
        due.addClassName("invoice-due");
        var top = new Div(title, due); top.addClassName("invoice-top");
        body.add(top);
        if (!invoice.readingIssues().isEmpty()) {
            var issues = new UnorderedList();
            invoice.readingIssues().forEach(message -> issues.add(new ListItem(ui.generated(message))));
            var warning = new Div(new H3(ui.t("Some values need attention")), issues);
            warning.addClassName("reading-warning"); body.add(warning);
        }
        body.add(fieldGrid(invoice.header()));
        var parties = new Div(group(invoice.seller()), group(invoice.buyer()));
        parties.addClassName("invoice-parties"); body.add(parties);
        body.add(new H3(ui.format("Invoice lines ({0})", invoice.lines().size())));
        if (invoice.lines().isEmpty()) body.add(new Paragraph(ui.t("No invoice lines were provided.")));
        else {
            var grid = new Grid<Line>();
            grid.addClassName("invoice-lines");
            grid.addComponentColumn(line -> {
                var name = field(line.fields(), "BT-153");
                var description = field(line.fields(), "BT-154");
                var identifier = field(line.fields(), "BT-126");
                var content = new Div(value(name));
                if (description.state() != State.MISSING && !sameText(description, name)) content.add(value(description));
                if (identifier.state() != State.MISSING && !sameText(identifier, name) && !sameText(identifier, description)) {
                    var id = value(identifier); id.addClassName("line-identifier"); content.add(id);
                }
                content.addClassName("line-name");
                return content;
            }).setHeader(ui.t("Item")).setWidth("180px").setFlexGrow(2);
            grid.addComponentColumn(line -> value(field(line.fields(), "BT-129"))).setHeader(ui.t("Quantity")).setWidth("90px").setFlexGrow(0);
            grid.addComponentColumn(line -> {
                var price = new Div(value(field(line.fields(), "BT-146")));
                var base = field(line.fields(), "BT-149");
                if (base.state() != State.MISSING) price.add(new Span(ui.german() ? "je " : "per "), value(base));
                return price;
            }).setHeader(ui.t("Net unit price")).setWidth("130px").setFlexGrow(1);
            grid.addComponentColumn(line -> {
                var taxes = new Div();
                for (var tax : line.taxes()) taxes.add(new Div(value(field(tax.fields(), "BT-151")), value(field(tax.fields(), "BT-152"))));
                if (line.taxes().isEmpty()) taxes.add(new Span(ui.german() ? "Nicht angegeben" : "Not provided"));
                return taxes;
            }).setHeader(ui.t("VAT")).setWidth("170px").setFlexGrow(0);
            grid.addComponentColumn(line -> value(field(line.fields(), "BT-131"))).setHeader(ui.t("Net amount")).setWidth("100px").setFlexGrow(1);
            grid.addComponentColumn(line -> {
                var detailFields = line.fields().stream().filter(f -> !f.term().equals("BT-154")
                        || !sameText(f, field(line.fields(), "BT-153"))).toList();
                var details = new Div(fieldGrid(detailFields, false));
                line.adjustments().forEach(adjustment -> details.add(group(adjustment, false)));
                var expansion = new Details(ui.t("Line details"), details);
                expansion.addClassName("invoice-line-details"); return expansion;
            }).setHeader(ui.t("Details")).setWidth("140px").setFlexGrow(0);
            grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
            grid.setItems(invoice.lines());
            if (invoice.lines().size() <= 5) grid.setAllRowsVisible(true);
            else grid.setHeight("360px");
            body.add(grid, lineConsistency(invoice), note("Scroll the table horizontally to see all columns on a smaller screen."));
        }
        if (!invoice.adjustments().isEmpty()) {
            var content = new Div(); invoice.adjustments().forEach(g -> content.add(group(g)));
            var adjustments = new Details(ui.format("Document allowances and charges ({0})", invoice.adjustments().size()), content);
            adjustments.addClassName("invoice-extra"); body.add(adjustments);
        }
        body.add(new H3(ui.t("VAT breakdown")));
        if (invoice.vat().isEmpty()) body.add(note("No VAT breakdown was provided."));
        else {
            var taxes = new Div(); taxes.addClassName("invoice-tax-list");
            for (var tax : invoice.vat()) taxes.add(fieldGrid(tax.fields()));
            body.add(taxes);
        }
        var totals = new Div(new H3(ui.t("Invoice totals"))); totals.addClassName("invoice-totals");
        for (var value : invoice.totals()) {
            if (!visible(value)) continue;
            var row = new Div(new Span(ui.t(value.label())), value(value));
            row.addClassNames("total-row", value.term().equals("BT-115") ? "total-due" : "total-standard");
            register(value.term(), row); totals.add(row);
        }
        body.add(totals, note("Amounts are formatted without changing their value. Hover over a value to see the original XML text."));
        body.add(new H3(ui.t("Payment details")));
        if (invoice.payments().isEmpty()) body.add(note("No payment instructions were provided."));
        else {
            var payments = new Div(); payments.addClassName("invoice-payments");
            invoice.payments().forEach(g -> payments.add(group(g))); body.add(payments);
        }
        if (invoice.delivery().stream().anyMatch(f -> f.state() != State.MISSING)) {
            body.add(new H3(ui.t("Delivery and invoicing period")), fieldGrid(invoice.delivery()));
        }
        if (!invoice.notes().isEmpty()) {
            var notes = new Div(new H3(ui.t("Invoice notes"))); notes.addClassName("invoice-notes");
            invoice.notes().forEach(note -> {
                var row = new Div(); row.addClassName("invoice-note-row");
                if (!note.subject().isBlank()) {
                    var subject = new Span(note.subject()); subject.addClassName("note-subject");
                    Tooltip.forComponent(subject).withText("BT-21 · " + ui.t("Note subject code") + " · " + note.subject()); row.add(subject);
                }
                var text = new Paragraph(note.text());
                Tooltip.forComponent(text).withText("BT-22 · " + ui.t("Invoice note") + " · " + ui.t("XML value") + ": " + note.raw());
                row.add(text); notes.add(row);
            }); body.add(notes);
        }
        if (invoice.attachmentCount() > 0) body.add(note(ui.format("Supporting documents: {0}. Attachments are not opened or downloaded in this preview.", invoice.attachmentCount())));
        body.add(note("This view covers the main invoice fields. Additional XML fields may be present."));
    }

    boolean canLocate(String term) { return locations.containsKey(term) && locations.get(term).size() == 1; }
    void locate(String term) {
        if (canLocate(term)) locations.get(term).getFirst().getElement().executeJs(
                "this.scrollIntoView({behavior:'smooth',block:'center'}); this.focus({preventScroll:true});");
    }
    private void register(String term, Component component) {
        component.getElement().setAttribute("tabindex", "-1");
        locations.computeIfAbsent(term, ignored -> new ArrayList<>()).add(component);
    }
    private Div group(Group group) { return group(group, true); }
    private Div group(Group group, boolean locate) {
        var div = new Div(new H3(ui.generated(group.title())), fieldGrid(group.fields(), locate));
        div.addClassName("invoice-group"); return div;
    }
    private Div fieldGrid(List<Field> fields) { return fieldGrid(fields, true); }
    private Div fieldGrid(List<Field> fields, boolean locate) {
        var grid = new Div(); grid.addClassName("invoice-fields");
        fields.stream().filter(InvoiceDocumentView::visible).forEach(field -> {
            var label = new Span(ui.t(field.label())); label.addClassName("invoice-field-label");
            label.getElement().setAttribute("data-bt", field.term());
            Tooltip.forComponent(label).withText(sourceTooltip(field));
            var div = new Div(label, value(field)); div.addClassName("invoice-field");
            if (locate) register(field.term(), div);
            grid.add(div);
        });
        return grid;
    }
    private static boolean visible(Field field) { return field.state() != State.MISSING || ESSENTIAL.contains(field.term()); }
    private Span value(Field field) {
        var span = new Span(display.format(field));
        span.addClassNames("invoice-value", "value-" + field.state().name().toLowerCase());
        if (field.state() == State.UNPARSEABLE) span.add(new Span(ui.german() ? " · nicht lesbarer Wert" : " · unrecognized value"));
        Tooltip.forComponent(span).withText(sourceTooltip(field));
        if (InvoiceDisplay.isIban(field)) {
            span.addClassName("iban-value");
            var copy = new Button(ui.t("Copy"));
            copy.setAriaLabel(ui.t("Copy IBAN"));
            copy.addClassName("copy-iban");
            copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            copy.addClickListener(event -> copy.getElement().executeJs(
                    "if (!navigator.clipboard) return false; return navigator.clipboard.writeText($0).then(() => true).catch(() => false);",
                    InvoiceDisplay.copyIban(field)).then(Boolean.class, success -> copy.setText(success
                            ? ui.t("Copied") : ui.t("Copy failed"))));
            span.add(copy);
        }
        return span;
    }
    private String sourceTooltip(Field field) {
        return field.term() + " · " + ui.t(field.label()) + " · " + ui.t("XML value") + ": "
                + (field.raw().isBlank() ? ui.t("(missing)") : field.raw())
                + (field.unit().isBlank() ? "" : " · "
                + (field.term().equals("BT-129") ? "BT-130 " + ui.t("Unit code")
                : field.term().equals("BT-149") ? "BT-150 " + ui.t("Unit code") : ui.t("Unit/currency code")) + ": " + field.unit());
    }
    private static boolean sameText(Field a, Field b) { return a.raw().strip().equals(b.raw().strip()); }

    private Component lineConsistency(InvoiceDocument invoice) {
        var check = LineTotalCheck.of(invoice);
        String message = switch (check.status()) {
            case MATCH -> ui.german() ? "✓ Positionssummen stimmen überein" : "✓ Line totals consistent";
            case MISMATCH -> ui.german() ? "! Positionssummen weichen ab" : "! Line totals differ";
            case UNAVAILABLE -> ui.german() ? "Positionssummen nicht vergleichbar" : "Line comparison unavailable";
        };
        var content = new Div(new Span(message));
        content.addClassNames("line-consistency", "consistency-" + check.status().name().toLowerCase());
        if (check.status() == LineTotalCheck.Status.MISMATCH) {
            content.add(new Paragraph(ui.format("Line sum: {0} · Stated line subtotal: {1}",
                    display.money(check.sum().orElseThrow(), check.currency()),
                    display.money(check.stated().orElseThrow(), check.currency()))));
        }
        Tooltip.forComponent(content).withText(ui.t("Display check") + " · " + ui.t(check.reason()) + " " + ui.t("The official validation result is unchanged."));
        return content;
    }
    private Paragraph note(String text) {
        var p = new Paragraph(ui.t(text)); p.addClassNames("small", "muted", "invoice-note"); return p;
    }
}
