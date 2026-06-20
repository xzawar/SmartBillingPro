package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.Ui;

import javax.swing.JTextField;
import java.awt.Window;

/** Add or edit a supplier. */
public class SupplierDialog extends FormDialog {

    private final AppContext context;
    private final Supplier supplier;
    private final boolean isNew;

    private final JTextField nameField = new JTextField(18);
    private final JTextField phoneField = new JTextField(18);
    private final JTextField addressField = new JTextField(18);
    private final JTextField notesField = new JTextField(18);

    public SupplierDialog(Window owner, AppContext context, Supplier existing) {
        super(owner, existing == null ? "Add Supplier" : "Edit Supplier");
        this.context = context;
        this.isNew = existing == null;
        this.supplier = existing == null ? new Supplier() : existing;

        Ui.styleField(nameField);
        Ui.styleField(phoneField);
        Ui.styleField(addressField);
        Ui.styleField(notesField);

        addField("Name", nameField);
        addField("Phone", phoneField);
        addField("Address", addressField);
        addField("Notes", notesField);

        if (!isNew) {
            nameField.setText(supplier.getName());
            phoneField.setText(supplier.getPhone());
            addressField.setText(supplier.getAddress());
            notesField.setText(supplier.getNotes());
        }
    }

    @Override
    protected boolean onSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            error("Supplier name is required.");
            return false;
        }
        supplier.setName(name);
        supplier.setPhone(phoneField.getText().trim());
        supplier.setAddress(addressField.getText().trim());
        supplier.setNotes(notesField.getText().trim());
        if (isNew) {
            context.getSupplierService().create(supplier);
        } else {
            context.getSupplierService().update(supplier);
        }
        return true;
    }
}
