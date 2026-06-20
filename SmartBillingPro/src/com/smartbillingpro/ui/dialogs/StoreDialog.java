package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Store;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.Ui;

import javax.swing.JCheckBox;
import javax.swing.JTextField;
import java.awt.Window;

/**
 * Add a new store, or edit an existing one. Creating a store sets up a brand-new
 * empty workspace for a shop owner (its own products, customers, sales and
 * settings, all starting from zero).
 */
public class StoreDialog extends FormDialog {

    private final AppContext context;
    private final Store store;
    private final boolean isNew;

    private final JTextField nameField = new JTextField(18);
    private final JTextField phoneField = new JTextField(18);
    private final JTextField addressField = new JTextField(18);
    private final JCheckBox activeCheck = new JCheckBox("Store is active");

    public StoreDialog(Window owner, AppContext context, Store existing) {
        super(owner, existing == null ? "Add Store" : "Edit Store");
        this.context = context;
        this.isNew = existing == null;
        this.store = existing == null ? new Store() : existing;

        Ui.styleField(nameField);
        Ui.styleField(phoneField);
        Ui.styleField(addressField);
        activeCheck.setOpaque(false);
        activeCheck.setSelected(true);

        addField("Store Name", nameField);
        addField("Phone", phoneField);
        addField("Address", addressField);
        addField("Status", activeCheck);

        if (!isNew) {
            nameField.setText(store.getName());
            phoneField.setText(store.getPhone());
            addressField.setText(store.getAddress());
            activeCheck.setSelected(store.isActive());
        }
    }

    @Override
    protected boolean onSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            error("Store name is required.");
            return false;
        }
        store.setName(name);
        store.setPhone(phoneField.getText().trim());
        store.setAddress(addressField.getText().trim());
        store.setActive(activeCheck.isSelected());
        if (isNew) {
            context.getStoreService().create(store);
        } else {
            context.getStoreService().update(store);
        }
        return true;
    }
}
