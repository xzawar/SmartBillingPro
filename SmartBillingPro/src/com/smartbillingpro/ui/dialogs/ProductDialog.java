package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.util.Money;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Window;
import java.math.BigDecimal;
import java.util.List;

/** Add or edit a product, including its supplier and prices. */
public class ProductDialog extends FormDialog {

    private final AppContext context;
    private final Product product;
    private final boolean isNew;

    private final JTextField nameField = new JTextField(18);
    private final JTextField skuField = new JTextField(18);
    private final JTextField categoryField = new JTextField(18);
    private final JTextField costField = new JTextField(18);
    private final JTextField saleField = new JTextField(18);
    private final JTextField quantityField = new JTextField(18);
    private final JComboBox<Supplier> supplierCombo = new JComboBox<>();

    public ProductDialog(Window owner, AppContext context, Product existing) {
        super(owner, existing == null ? "Add Product" : "Edit Product");
        this.context = context;
        this.isNew = existing == null;
        this.product = existing == null ? new Product() : existing;

        for (JTextField field : new JTextField[] {nameField, skuField, categoryField,
                costField, saleField, quantityField}) {
            Ui.styleField(field);
        }
        Ui.styleComponent(supplierCombo);

        Supplier none = new Supplier();
        none.setId(0);
        none.setName("\u2014 None \u2014");
        supplierCombo.addItem(none);
        List<Supplier> suppliers = context.getSupplierService().all();
        for (Supplier supplier : suppliers) {
            supplierCombo.addItem(supplier);
        }

        addField("Name", nameField);
        addField("SKU / Barcode", skuField);
        addField("Category", categoryField);
        addField("Cost Price", costField);
        addField("Sale Price", saleField);
        addField("Quantity", quantityField);
        addField("Supplier", supplierCombo);

        if (!isNew) {
            nameField.setText(product.getName());
            skuField.setText(product.getSku());
            categoryField.setText(product.getCategory());
            costField.setText(product.getCostPrice().toPlainString());
            saleField.setText(product.getSalePrice().toPlainString());
            quantityField.setText(String.valueOf(product.getQuantity()));
            if (product.getSupplierId() != null) {
                for (int i = 0; i < supplierCombo.getItemCount(); i++) {
                    if (supplierCombo.getItemAt(i).getId() == product.getSupplierId()) {
                        supplierCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected boolean onSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            error("Product name is required.");
            return false;
        }
        int quantity;
        try {
            String raw = quantityField.getText().trim();
            quantity = raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            error("Quantity must be a whole number.");
            return false;
        }
        if (quantity < 0) {
            error("Quantity cannot be negative.");
            return false;
        }
        BigDecimal cost = Money.parse(costField.getText());
        BigDecimal sale = Money.parse(saleField.getText());

        product.setName(name);
        product.setSku(skuField.getText().trim());
        product.setCategory(categoryField.getText().trim());
        product.setCostPrice(cost);
        product.setSalePrice(sale);
        product.setQuantity(quantity);
        Supplier selected = (Supplier) supplierCombo.getSelectedItem();
        if (selected == null || selected.getId() == 0) {
            product.setSupplierId(null);
        } else {
            product.setSupplierId(selected.getId());
        }

        if (isNew) {
            context.getInventoryService().create(product);
        } else {
            context.getInventoryService().update(product);
        }
        return true;
    }
}
