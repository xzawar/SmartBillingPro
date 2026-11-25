package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Customer;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Text;

import javax.swing.JCheckBox;
import javax.swing.JTextField;
import java.awt.Component;

/** Add / edit a customer. The opening balance can only be set when adding. */
public final class CustomerDialog extends FormDialog {

    private final AppContext ctx;
    private final Customer existing;
    private final JTextField name = Ui.field("Full name");
    private final JTextField phone = Ui.field("e.g. 0300-1234567");
    private final JTextField address = Ui.field("optional");
    private final JTextField notes = Ui.field("optional");
    private final JTextField opening = Ui.money("0.00");
    private final JCheckBox active = new JCheckBox("Active (shown in lists)");
    private long savedId;

    public CustomerDialog(Component parent, AppContext ctx, Customer existing) {
        super(parent, existing == null ? "Add customer" : "Edit customer", "Save customer");
        this.ctx = ctx;
        this.existing = existing;
        active.setOpaque(false);
        if (existing == null) {
            setBody(Ui.vbox(12, Ui.row("Name *", name), Ui.grid(2, 12, 0, Ui.row("Phone", phone), Ui.row("Address", address)),
                    Ui.row("Notes", notes), Ui.row("Opening balance (amount the customer already owes)", opening,
                            "Leave 0 for a new customer. Use a minus sign for an advance paid by the customer.")));
        } else {
            name.setText(existing.name());
            phone.setText(Text.trim(existing.phone()));
            address.setText(Text.trim(existing.address()));
            notes.setText(Text.trim(existing.notes()));
            active.setSelected(existing.active());
            setBody(Ui.vbox(12, Ui.row("Name *", name), Ui.grid(2, 12, 0, Ui.row("Phone", phone), Ui.row("Address", address)),
                    Ui.row("Notes", notes), active,
                    Ui.muted("To correct the balance, use Ledgers › Adjust balance (history is kept).")));
        }
    }

    public long savedId() {
        return savedId;
    }

    @Override
    protected boolean onSave() {
        long open = 0;
        if (existing == null && !opening.getText().isBlank()) {
            String t = opening.getText().trim();
            boolean negative = t.startsWith("-");
            open = com.smartbillingpro.util.Money.parse(negative ? t.substring(1) : t) * (negative ? -1 : 1);
        }
        savedId = ctx.parties().saveCustomer(existing == null ? null : existing.id(), name.getText(), phone.getText(),
                address.getText(), notes.getText(), open, existing == null || active.isSelected());
        return true;
    }
}
