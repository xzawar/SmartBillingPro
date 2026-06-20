package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Customer;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.Ui;

import javax.swing.JTextField;
import java.awt.Window;

/** Add or edit a customer. */
public class CustomerDialog extends FormDialog {

    private final AppContext context;
    private final Customer customer;
    private final boolean isNew;

    private final JTextField nameField = new JTextField(18);
    private final JTextField phoneField = new JTextField(18);
    private final JTextField addressField = new JTextField(18);

    public CustomerDialog(Window owner, AppContext context, Customer existing) {
        super(owner, existing == null ? "Add Customer" : "Edit Customer");
        this.context = context;
        this.isNew = existing == null;
        this.customer = existing == null ? new Customer() : existing;

        Ui.styleField(nameField);
        Ui.styleField(phoneField);
        Ui.styleField(addressField);

        addField("Name", nameField);
        addField("Phone", phoneField);
        addField("Address", addressField);

        if (!isNew) {
            nameField.setText(customer.getName());
            phoneField.setText(customer.getPhone());
            addressField.setText(customer.getAddress());
        }
    }

    public Customer getCustomer() {
        return customer;
    }

    @Override
    protected boolean onSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            error("Customer name is required.");
            return false;
        }
        customer.setName(name);
        customer.setPhone(phoneField.getText().trim());
        customer.setAddress(addressField.getText().trim());
        if (isNew) {
            context.getCustomerService().create(customer);
        } else {
            context.getCustomerService().update(customer);
        }
        return true;
    }
}
