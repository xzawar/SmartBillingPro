package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.ui.Collapsible;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Text;

import javax.swing.JTextField;
import java.awt.Component;

/** Add / edit a supplier: name and phone up front, the rest only if wanted. */
public final class SupplierDialog extends FormDialog {

    private final AppContext ctx;
    private final Supplier existing;
    private final JTextField name = Ui.field("e.g. Hamza Traders");
    private final JTextField phone = Ui.field("e.g. 0300 1234567");
    private final JTextField address = Ui.field("optional");
    private final JTextField business = Ui.field("e.g. NTN, what they supply");
    private final JTextField notes = Ui.field("optional");
    private final JTextField opening = Ui.money("0");
    private long savedId;

    public SupplierDialog(Component parent, AppContext ctx, Supplier existing) {
        super(parent, existing == null ? "New supplier" : "Edit supplier", existing == null ? "Add supplier" : "Save");
        this.ctx = ctx;
        this.existing = existing;
        Collapsible more;
        if (existing == null) {
            more = new Collapsible("Add more details", Ui.vbox(12, Ui.row("Address", address),
                    Ui.row("Business details", business), Ui.row("Notes", notes),
                    Ui.row("You already owe them from before", opening,
                            "Only if you owed this supplier money before you started using this app.")));
        } else {
            name.setText(existing.name());
            phone.setText(Text.trim(existing.phone()));
            address.setText(Text.trim(existing.address()));
            business.setText(Text.trim(existing.businessInfo()));
            notes.setText(Text.trim(existing.notes()));
            more = new Collapsible("Show more details", Ui.vbox(12, Ui.row("Address", address),
                    Ui.row("Business details", business), Ui.row("Notes", notes)));
            more.setExpanded(!address.getText().isBlank() || !business.getText().isBlank() || !notes.getText().isBlank());
        }
        setBody(Ui.vbox(14, Ui.row("Name", name), Ui.row("Phone (optional)", phone), more));
    }

    public long savedId() {
        return savedId;
    }

    @Override
    protected boolean onSave() {
        long open = existing == null ? Ui.signedMoneyValue(opening, "the amount you already owe") : 0;
        savedId = ctx.parties().saveSupplier(existing == null ? null : existing.id(), name.getText(), phone.getText(),
                address.getText(), business.getText(), notes.getText(), open, existing == null || existing.active());
        return true;
    }
}
