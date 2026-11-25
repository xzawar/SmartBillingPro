package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Component;

/** Receive money from a customer, or pay a supplier, against their account. */
public final class PaymentDialog extends FormDialog {

    private final AppContext ctx;
    private final PartyType type;
    private final long partyId;
    private final long balance;
    private final JTextField amount = Ui.money("0.00");
    private final JComboBox<PaymentMethod> method = new JComboBox<>();
    private final JTextField note = Ui.field("optional, e.g. cheque no.");
    private final JLabel after = Ui.heading(" ");

    public PaymentDialog(Component parent, AppContext ctx, PartyType type, long partyId, String partyName) {
        super(parent, type == PartyType.CUSTOMER ? "Receive payment" : "Pay supplier",
                type == PartyType.CUSTOMER ? "Save payment" : "Save payment");
        this.ctx = ctx;
        this.type = type;
        this.partyId = partyId;
        this.balance = ctx.parties().balance(type, partyId);
        setSubtitle(partyName + "  •  " + (balance > 0 ? (type == PartyType.CUSTOMER ? "owes " : "you owe ")
                + Money.format(balance) : balance < 0 ? "advance " + Money.format(-balance) : "no balance due"));
        for (PaymentMethod m : ctx.settings().paymentMethods(true)) {
            method.addItem(m);
        }
        if (balance > 0) {
            amount.setText(Money.plain(balance));
        }
        Ui.onChange(amount, this::preview);
        after.setFont(Theme.bold(14));
        setBody(Ui.vbox(12, Ui.grid(2, 12, 0, Ui.row("Amount *", amount), Ui.row("Paid by", method)),
                Ui.row("Note", note), after));
        preview();
        amount.selectAll();
    }

    private void preview() {
        try {
            long a = Money.parse(amount.getText());
            long rest = balance - a;
            after.setForeground(rest > 0 ? Theme.WARN : Theme.GOOD);
            after.setText(rest > 0 ? "Still outstanding after this: " + Money.format(rest)
                    : rest == 0 ? "✓ Account fully settled" : "Advance after this: " + Money.format(-rest));
        } catch (IllegalArgumentException e) {
            after.setText(" ");
        }
    }

    @Override
    protected boolean onSave() {
        long a = Ui.moneyValue(amount, "the amount", true);
        PaymentMethod m = (PaymentMethod) method.getSelectedItem();
        if (m == null) {
            throw new com.smartbillingpro.db.AppException("Please choose a payment method.");
        }
        if (type == PartyType.CUSTOMER) {
            ctx.parties().receiveFromCustomer(partyId, a, m.id(), note.getText());
        } else {
            ctx.parties().payToSupplier(partyId, a, m.id(), note.getText());
        }
        return true;
    }
}
