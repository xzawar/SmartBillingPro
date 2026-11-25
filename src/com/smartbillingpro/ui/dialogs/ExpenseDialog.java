package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.db.AppException;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.ui.Collapsible;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.PillGroup;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Dates;

import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import java.awt.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Record money the shop spent: what it was for and how much. Today's date and
 * the usual payment method are assumed unless changed under "more details".
 */
public final class ExpenseDialog extends FormDialog {

    private static final String OTHER = "Something else…";
    private static final List<String> COMMON = List.of("Electricity", "Rent", "Salaries", "Transport", "Tea & food",
            "Repairs");

    private final AppContext ctx;
    private final PillGroup<String> category = new PillGroup<>();
    private final JTextField other = Ui.field("e.g. Shop cleaning");
    private final JTextField amount = Ui.money("0");
    private final JSpinner date = new JSpinner(new SpinnerDateModel(new Date(), null, new Date(), Calendar.DAY_OF_MONTH));
    private final JComboBox<PaymentMethod> method = new JComboBox<>();
    private final JTextField note = Ui.field("optional");

    public ExpenseDialog(Component parent, AppContext ctx) {
        super(parent, "New expense", "Save expense");
        this.ctx = ctx;
        setSubtitle("Money the shop spent, like rent or electricity. It is taken off your profit in reports.");
        List<String> cats = new ArrayList<>();
        for (String c : ctx.expenses().categories()) {
            if (cats.size() < 8 && cats.stream().noneMatch(x -> x.equalsIgnoreCase(c))) {
                cats.add(c);
            }
        }
        for (String c : COMMON) {
            if (cats.size() < 8 && cats.stream().noneMatch(x -> x.equalsIgnoreCase(c))) {
                cats.add(c);
            }
        }
        cats.add(OTHER);
        category.setItems(cats, s -> s);
        category.onChange(c -> {
            other.setVisible(OTHER.equals(c));
            if (OTHER.equals(c)) {
                other.requestFocusInWindow();
            } else {
                amount.requestFocusInWindow();
            }
            revalidate();
        });
        other.setVisible(OTHER.equals(category.selected()));
        date.setEditor(new JSpinner.DateEditor(date, Dates.displayPattern()));
        Long def = ctx.settings().get().defaultMethodId();
        for (PaymentMethod m : ctx.settings().paymentMethods(true)) {
            method.addItem(m);
            if ((def != null && m.id() == def) || (def == null && m.cash() && method.getSelectedIndex() <= 0)) {
                method.setSelectedItem(m);
            }
        }
        amount.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "font:+4");
        Collapsible more = new Collapsible("Change the date, how it was paid, or add a note",
                Ui.vbox(12, Ui.grid(2, 12, 0, Ui.row("Date", date), Ui.row("Paid by", method)), Ui.row("Note", note)));
        setBody(Ui.vbox(14, Ui.row("What was it for?", Ui.vbox(8, category, other)), Ui.row("How much?", amount), more));
    }

    @Override
    protected boolean onSave() {
        String c = category.selected();
        if (OTHER.equals(c)) {
            c = other.getText().trim();
            if (c.isEmpty()) {
                other.requestFocusInWindow();
                throw new AppException("Please type what the money was spent on.");
            }
        }
        long a = Ui.moneyValue(amount, "the amount", true);
        if (a <= 0) {
            amount.requestFocusInWindow();
            throw new AppException("The amount must be more than zero.");
        }
        LocalDate d = ((Date) date.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        PaymentMethod m = (PaymentMethod) method.getSelectedItem();
        if (m == null) {
            throw new AppException("Add a way to pay (e.g. Cash) in Tools & Settings first.");
        }
        ctx.expenses().add(d, c, a, m.id(), note.getText());
        return true;
    }
}
