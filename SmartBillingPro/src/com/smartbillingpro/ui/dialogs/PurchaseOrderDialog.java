package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.PurchaseOrder;
import com.smartbillingpro.model.PurchaseOrderItem;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Receive stock from a supplier: pick products, quantities and unit costs. */
public class PurchaseOrderDialog extends JDialog {

    private final AppContext context;
    private final List<PurchaseOrderItem> lines = new ArrayList<>();
    private final List<Product> products;

    private final JComboBox<Supplier> supplierCombo = new JComboBox<>();
    private final JComboBox<Product> productCombo = new JComboBox<>();
    private final JTextField qtyField = new JTextField("1", 5);
    private final JTextField costField = new JTextField(8);
    private final DefaultTableModel model;
    private final JLabel totalLabel = new JLabel();

    private boolean received = false;

    public PurchaseOrderDialog(Window owner, AppContext context) {
        super(owner, "Receive Stock", ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.products = context.getInventoryService().all();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        for (Supplier supplier : context.getSupplierService().all()) {
            supplierCombo.addItem(supplier);
        }
        for (Product product : products) {
            productCombo.addItem(product);
        }
        Ui.styleComponent(supplierCombo);
        Ui.styleComponent(productCombo);
        Ui.styleField(qtyField);
        Ui.styleField(costField);
        productCombo.addActionListener(e -> prefillCost());
        prefillCost();

        GradientPanel header = new GradientPanel(new BorderLayout());
        header.setColors(Theme.PRIMARY, Theme.ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
        JLabel heading = new JLabel("Receive Stock from Supplier");
        heading.setFont(Theme.bold(17));
        heading.setForeground(Color.WHITE);
        header.add(heading, BorderLayout.WEST);

        JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        entry.setBackground(Theme.BG);
        entry.add(Ui.formLabel("Supplier"));
        entry.add(supplierCombo);
        entry.add(Ui.formLabel("Product"));
        entry.add(productCombo);
        entry.add(Ui.formLabel("Qty"));
        entry.add(qtyField);
        entry.add(Ui.formLabel("Unit Cost"));
        entry.add(costField);
        RoundedButton add = new RoundedButton("Add Line", Theme.PRIMARY, Color.WHITE);
        add.addActionListener(e -> addLine());
        entry.add(add);

        model = new DefaultTableModel(new Object[] {"Product", "Qty", "Unit Cost", "Line Total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        StripedTable table = new StripedTable(model);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(Theme.BG);
        center.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        center.add(entry, BorderLayout.NORTH);
        center.add(Ui.scroll(table), BorderLayout.CENTER);

        totalLabel.setFont(Theme.bold(16));
        totalLabel.setForeground(Theme.TEXT);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.BG);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        footer.add(totalLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        RoundedButton remove = new RoundedButton("Remove Selected", Theme.withAlpha(Theme.DANGER, 35), Theme.DANGER);
        remove.addActionListener(e -> removeSelected(table.getSelectedRow()));
        RoundedButton receive = new RoundedButton("Receive Stock", Theme.SUCCESS, Color.WHITE);
        receive.addActionListener(e -> receive());
        buttons.add(remove);
        buttons.add(receive);
        footer.add(buttons, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(new Dimension(720, 520));
        setLocationRelativeTo(owner);
        refreshTotal();
    }

    private void prefillCost() {
        Product selected = (Product) productCombo.getSelectedItem();
        if (selected != null) {
            costField.setText(selected.getCostPrice().toPlainString());
        }
    }

    private void addLine() {
        Product product = (Product) productCombo.getSelectedItem();
        if (product == null) {
            JOptionPane.showMessageDialog(this, "Add a product to the catalogue first.");
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Quantity must be a whole number.");
            return;
        }
        if (qty <= 0) {
            JOptionPane.showMessageDialog(this, "Quantity must be at least 1.");
            return;
        }
        BigDecimal cost = Money.parse(costField.getText());
        lines.add(new PurchaseOrderItem(product.getId(), product.getName(), qty, cost));
        model.addRow(new Object[] {product.getName(), qty,
                Money.format(symbol(), cost),
                Money.format(symbol(), cost.multiply(BigDecimal.valueOf(qty)))});
        refreshTotal();
    }

    private void removeSelected(int row) {
        if (row < 0 || row >= lines.size()) {
            return;
        }
        lines.remove(row);
        model.removeRow(row);
        refreshTotal();
    }

    private void refreshTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem item : lines) {
            total = total.add(item.getLineTotal());
        }
        totalLabel.setText("Order Total:  " + Money.format(symbol(), total));
    }

    private void receive() {
        Supplier supplier = (Supplier) supplierCombo.getSelectedItem();
        if (supplier == null) {
            JOptionPane.showMessageDialog(this, "Please select a supplier.");
            return;
        }
        if (lines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one line before receiving.");
            return;
        }
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplierId(supplier.getId());
        order.setSupplierName(supplier.getName());
        order.setItems(new ArrayList<>(lines));
        context.getPurchaseService().receive(order);
        received = true;
        JOptionPane.showMessageDialog(this, "Stock received and inventory updated.");
        dispose();
    }

    private String symbol() {
        return context.getSettings().getCurrencySymbol();
    }

    public boolean isReceived() {
        return received;
    }
}
