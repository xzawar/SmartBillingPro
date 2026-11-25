package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Customer;
import com.smartbillingpro.ui.Collapsible;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Text;

import javax.swing.JTextField;
import java.awt.Component;

/**
 * Add / edit a customer. Only the name is needed; phone is offered because it
 * helps find the customer again. Everything else is tucked under "more details".
 */
public final class CustomerDialog extends FormDialog {

    private final AppContext ctx;
    private final Customer existing;
    private final JTextField name = Ui.field("e.g. Ali Raza");
    private final JTextField phone = Ui.field("e.g. 0300 1234567");
    private final JTextField address = Ui.field("optional");
    private final JTextField notes = Ui.field("optional");
    private final JTextField opening = Ui.money("0");
    private long savedId;

    public CustomerDialog(Component parent, AppContext ctx, Customer existing) {
        this(parent, ctx, existing, null);
    }

    /** {@code prefillName} fills in the name when adding (may be null). */
    public CustomerDialog(Component parent, AppContext ctx, Customer existing, String prefillName) {
        super(parent, existing == null ? "New customer" : "Edit customer", existing == null ? "Add customer" : "Save");
        this.ctx = ctx;
        this.existing = existing;
        Collapsible more;
        if (existing == null) {
            name.setText(Text.trim(prefillName));
            more = new Collapsible("Add more details", Ui.vbox(12, Ui.row("Address", address), Ui.row("Notes", notes),
                    Ui.row("Already owes you from before", opening,
                            "Only if this customer owed you money before you started using this app.")));
        } else {
            name.setText(existing.name());
            phone.setText(Text.trim(existing.phone()));
            address.setText(Text.trim(existing.address()));
            notes.setText(Text.trim(existing.notes()));
            more = new Collapsible("Show more details", Ui.vbox(12, Ui.row("Address", address), Ui.row("Notes", notes)));
            more.setExpanded(!address.getText().isBlank() || !notes.getText().isBlank());
        }
        setBody(Ui.vbox(14, Ui.row("Name", name), Ui.row("Phone (optional)", phone), more));
    }

    public long savedId() {
        return savedId;
    }

    @Override
    protected boolean onSave() {
        long open = existing == null ? Ui.signedMoneyValue(opening, "the amount already owed") : 0;
        savedId = ctx.parties().saveCustomer(existing == null ? null : existing.id(), name.getText(), phone.getText(),
                address.getText(), notes.getText(), open, existing == null || existing.active());
        return true;
    }
}
