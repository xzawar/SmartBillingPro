package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import javax.swing.JCheckBox;
import javax.swing.JTextField;
import java.awt.Component;

/** Add / edit a supplier. */
public final class SupplierDialog extends FormDialog {

    private final AppContext ctx;
    private final Supplier existing;
    private final JTextField name = Ui.field("Supplier or company name");
    private final JTextField phone = Ui.field("optional");
    private final JTextField address = Ui.field("optional");
    private final JTextField business = Ui.field("e.g. NTN, distributor of…");
    private final JTextField notes = Ui.field("optional");
    private final JTextField opening = Ui.money("0.00");
    private final JCheckBox active = new JCheckBox("Active (shown in lists)");
    private long savedId;

    public SupplierDialog(Component parent, AppContext ctx, Supplier existing) {
        super(parent, existing == null ? "Add supplier" : "Edit supplier", "Save supplier");
        this.ctx = ctx;
        this.existing = existing;
        active.setOpaque(false);
        if (existing != null) {
            name.setText(existing.name());
            phone.setText(Text.trim(existing.phone()));
            address.setText(Text.trim(existing.address()));
            business.setText(Text.trim(existing.businessInfo()));
            notes.setText(Text.trim(existing.notes()));
            active.setSelected(existing.active());
        }
        setBody(Ui.vbox(12, Ui.row("Name *", name), Ui.grid(2, 12, 0, Ui.row("Phone", phone), Ui.row("Address", address)),
                Ui.row("Business information", business), Ui.row("Notes", notes),
                existing == null ? Ui.row("Opening balance (amount you already owe this supplier)", opening,
                        "Leave 0 if nothing is owed. Use a minus sign for an advance you paid.") : active));
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
            open = Money.parse(negative ? t.substring(1) : t) * (negative ? -1 : 1);
        }
        savedId = ctx.parties().saveSupplier(existing == null ? null : existing.id(), name.getText(), phone.getText(),
                address.getText(), business.getText(), notes.getText(), open, existing == null || active.isSelected());
        return true;
    }
}
