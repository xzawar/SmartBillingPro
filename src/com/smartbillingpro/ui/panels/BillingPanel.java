package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.Invoice;
import com.smartbillingpro.model.InvoiceItem;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.BillingService;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.PaymentDialog;
import com.smartbillingpro.ui.dialogs.TextPreviewDialog;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The point-of-sale screen, organised into three clear cards:
 * <ol>
 *   <li><b>Customer Information</b> &mdash; name, phone, address and cashier.</li>
 *   <li><b>Add Products</b> &mdash; search the catalogue and build the cart.</li>
 *   <li><b>Invoice Summary</b> &mdash; discount, tax, totals and "Generate Invoice".</li>
 * </ol>
 */
public class BillingPanel extends PanelBase {

    // ----- customer information ----------------------------------------------
    private final JTextField customerNameField = new JTextField(18);
    private final JTextField customerPhoneField = new JTextField(18);
    private final JTextField customerAddressField = new JTextField(18);
    private final JTextField cashierField = new JTextField(18);

    // ----- product search + catalogue ----------------------------------------
    private final DefaultTableModel catalogModel = new DefaultTableModel(
            new Object[] {"Product", "SKU", "Price", "Stock"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable catalogTable = new StripedTable(catalogModel);
    private final JTextField catalogSearch = new JTextField(16);
    private final JTextField qtyField = new JTextField("1", 4);
    private final List<Product> catalogRows = new ArrayList<>();

    // ----- cart (line items) --------------------------------------------------
    private final DefaultTableModel cartModel = new DefaultTableModel(
            new Object[] {"#", "Product", "Price", "Qty", "Total"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable cartTable = new StripedTable(cartModel);

    // ----- invoice summary ----------------------------------------------------
    private final JTextField discountField = new JTextField(8);
    private final JTextField taxField = new JTextField(6);
    private final JLabel subtotalLabel = new JLabel();
    private final JLabel discountLabel = new JLabel();
    private final JLabel taxLabel = new JLabel();
    private final JLabel totalLabel = new JLabel();

    private boolean syncing = false;

    public BillingPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Billing", "Enter the customer's details, add products, then generate the invoice");

        BillingService billing = context.getBillingService();
        if (billing.getTaxPercent().signum() == 0) {
            billing.setTaxPercent(settings().getDefaultTaxPercent());
        }

        JPanel layout = new JPanel(new BorderLayout(16, 0));
        layout.setOpaque(false);
        layout.add(buildLeftColumn(), BorderLayout.CENTER);
        layout.add(buildSummaryCard(), BorderLayout.EAST);
        setBody(layout);
    }

    // ----- left column: customer + products ----------------------------------

    private JComponent buildLeftColumn() {
        JPanel left = new JPanel(new BorderLayout(0, 14));
        left.setOpaque(false);
        left.add(buildCustomerCard(), BorderLayout.NORTH);
        left.add(buildProductsCard(), BorderLayout.CENTER);
        return left;
    }

    private RoundedPanel buildCustomerCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.add(Ui.title("Customer Information", 15), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 6, 6, 6);

        Ui.styleField(customerNameField);
        Ui.styleField(customerPhoneField);
        Ui.styleField(customerAddressField);
        Ui.styleField(cashierField);
        cashierField.setEditable(false);
        cashierField.setBackground(Theme.BG);

        addLabeled(grid, c, 0, 0, "Customer Name *", customerNameField);
        addLabeled(grid, c, 1, 0, "Phone Number", customerPhoneField);
        addLabeled(grid, c, 0, 1, "Address", customerAddressField);
        addLabeled(grid, c, 1, 1, "Cashier", cashierField);

        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    /** Add a stacked label + field to the two-column customer grid. */
    private void addLabeled(JPanel grid, GridBagConstraints c, int col, int row, String label, JComponent field) {
        JPanel cell = new JPanel(new BorderLayout(0, 4));
        cell.setOpaque(false);
        cell.add(Ui.formLabel(label), BorderLayout.NORTH);
        cell.add(field, BorderLayout.CENTER);
        c.gridx = col;
        c.gridy = row;
        grid.add(cell, c);
    }

    private RoundedPanel buildProductsCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(Ui.title("Add Products", 15), BorderLayout.WEST);
        card.add(header, BorderLayout.NORTH);

        // Search + a small results table, then the cart underneath.
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        JLabel glass = Ui.formLabel("Search");
        searchRow.add(glass, BorderLayout.WEST);
        Ui.styleField(catalogSearch);
        catalogSearch.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                reloadCatalog();
            }
        });
        searchRow.add(catalogSearch, BorderLayout.CENTER);
        JPanel addControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addControls.setOpaque(false);
        addControls.add(Ui.formLabel("Qty"));
        Ui.styleField(qtyField);
        qtyField.setPreferredSize(new Dimension(56, 32));
        addControls.add(qtyField);
        RoundedButton add = new RoundedButton("Add", Theme.PRIMARY, Color.WHITE);
        add.addActionListener(e -> addToCart());
        addControls.add(add);
        searchRow.add(addControls, BorderLayout.EAST);
        center.add(searchRow, BorderLayout.NORTH);

        JPanel tables = new JPanel(new GridLayout(2, 1, 0, 10));
        tables.setOpaque(false);

        catalogTable.setPreferredScrollableViewportSize(new Dimension(0, 120));
        JPanel catalogWrap = new JPanel(new BorderLayout(0, 4));
        catalogWrap.setOpaque(false);
        catalogWrap.add(Ui.formLabel("Search results (double-check stock before adding)"), BorderLayout.NORTH);
        catalogWrap.add(Ui.scroll(catalogTable), BorderLayout.CENTER);

        JPanel cartWrap = new JPanel(new BorderLayout(0, 4));
        cartWrap.setOpaque(false);
        JPanel cartHeader = new JPanel(new BorderLayout(8, 0));
        cartHeader.setOpaque(false);
        cartHeader.add(Ui.formLabel("Items in this bill"), BorderLayout.WEST);
        JPanel lineButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        lineButtons.setOpaque(false);
        RoundedButton minus = new RoundedButton("\u2212", Theme.withAlpha(Theme.MUTED, 30), Theme.TEXT);
        minus.addActionListener(e -> adjustSelected(-1));
        RoundedButton plus = new RoundedButton("+", Theme.withAlpha(Theme.PRIMARY, 30), Theme.PRIMARY);
        plus.addActionListener(e -> adjustSelected(1));
        RoundedButton remove = new RoundedButton("Remove", Theme.withAlpha(Theme.DANGER, 35), Theme.DANGER);
        remove.addActionListener(e -> removeSelected());
        lineButtons.add(minus);
        lineButtons.add(plus);
        lineButtons.add(remove);
        cartHeader.add(lineButtons, BorderLayout.EAST);
        cartWrap.add(cartHeader, BorderLayout.NORTH);
        cartWrap.add(Ui.scroll(cartTable), BorderLayout.CENTER);

        tables.add(catalogWrap);
        tables.add(cartWrap);
        center.add(tables, BorderLayout.CENTER);

        card.add(center, BorderLayout.CENTER);
        return card;
    }

    // ----- right column: invoice summary -------------------------------------

    private JComponent buildSummaryCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        card.setPreferredSize(new Dimension(320, 0));
        card.add(Ui.title("Invoice Summary", 15), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel inputs = new JPanel(new GridBagLayout());
        inputs.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        inputs.add(Ui.formLabel("Discount (Rs)"), c);
        c.gridx = 1;
        c.weightx = 1;
        Ui.styleField(discountField);
        discountField.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                applyDiscountAndTax();
            }
        });
        inputs.add(discountField, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        inputs.add(Ui.formLabel("Tax (%)"), c);
        c.gridx = 1;
        c.weightx = 1;
        Ui.styleField(taxField);
        taxField.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                applyDiscountAndTax();
            }
        });
        inputs.add(taxField, c);
        inputs.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(inputs);

        body.add(javax.swing.Box.createVerticalStrut(8));
        body.add(separator());
        body.add(javax.swing.Box.createVerticalStrut(8));

        subtotalLabel.setFont(Theme.regular(14));
        discountLabel.setFont(Theme.regular(14));
        taxLabel.setFont(Theme.regular(14));
        totalLabel.setFont(Theme.bold(20));
        totalLabel.setForeground(Theme.PRIMARY);
        for (JLabel label : new JLabel[] {subtotalLabel, discountLabel, taxLabel, totalLabel}) {
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setForeground(label == totalLabel ? Theme.PRIMARY : Theme.TEXT);
            body.add(label);
            body.add(javax.swing.Box.createVerticalStrut(6));
        }

        card.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(2, 1, 0, 8));
        actions.setOpaque(false);
        RoundedButton generate = new RoundedButton("Generate Invoice", Theme.SUCCESS, Color.WHITE);
        generate.addActionListener(e -> generateInvoice());
        RoundedButton clear = new RoundedButton("Clear Bill", Theme.withAlpha(Theme.DANGER, 35), Theme.DANGER);
        clear.addActionListener(e -> clearBill());
        actions.add(generate);
        actions.add(clear);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JComponent separator() {
        JPanel line = new JPanel();
        line.setBackground(Theme.CARD_BORDER);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    // ----- catalogue / cart actions ------------------------------------------

    private void addToCart() {
        int row = catalogTable.getSelectedRow();
        if (row < 0 || row >= catalogRows.size()) {
            JOptionPane.showMessageDialog(this, "Select a product from the search results to add.");
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Quantity must be a whole number.");
            return;
        }
        try {
            context.getBillingService().addProduct(catalogRows.get(row), qty);
            qtyField.setText("1");
            refreshCart();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
    }

    private void reloadCatalog() {
        String term = catalogSearch.getText().trim();
        List<Product> products = term.isEmpty()
                ? context.getInventoryService().all()
                : context.getInventoryService().search(term);
        String symbol = symbol();
        catalogRows.clear();
        catalogModel.setRowCount(0);
        for (Product product : products) {
            catalogRows.add(product);
            catalogModel.addRow(new Object[] {product.getName(), product.getSku(),
                    Money.format(symbol, product.getSalePrice()), product.getQuantity()});
        }
    }

    private InvoiceItem selectedItem() {
        int row = cartTable.getSelectedRow();
        List<InvoiceItem> items = context.getBillingService().getItems();
        if (row < 0 || row >= items.size()) {
            return null;
        }
        return items.get(row);
    }

    private void adjustSelected(int delta) {
        InvoiceItem item = selectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Select a line in the bill first.");
            return;
        }
        Product product = context.getInventoryService().findById(item.getProductId());
        int available = product == null ? item.getQuantity() : product.getQuantity();
        try {
            context.getBillingService().setLineQuantity(item.getProductId(), item.getQuantity() + delta, available);
            refreshCart();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
    }

    private void removeSelected() {
        InvoiceItem item = selectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Select a line in the bill to remove.");
            return;
        }
        context.getBillingService().removeLine(item.getProductId());
        refreshCart();
    }

    private void applyDiscountAndTax() {
        if (syncing) {
            return;
        }
        BillingService billing = context.getBillingService();
        billing.setDiscount(parseMoney(discountField.getText()));
        billing.setTaxPercent(parseMoney(taxField.getText()));
        refreshTotals();
    }

    private BigDecimal parseMoney(String text) {
        try {
            String trimmed = text == null ? "" : text.trim();
            return trimmed.isEmpty() ? BigDecimal.ZERO : new BigDecimal(trimmed);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    // ----- checkout -----------------------------------------------------------

    private void generateInvoice() {
        BillingService billing = context.getBillingService();
        if (billing.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one product before generating the invoice.");
            return;
        }
        String name = customerNameField.getText().trim();
        if (name.isEmpty()) {
            name = "Walk-in Customer";
        }
        String phone = customerPhoneField.getText().trim();
        String address = customerAddressField.getText().trim();
        billing.setCustomerInfo(name, phone, address);

        // If a phone number is given, remember the customer (and their loyalty
        // points) by looking them up or creating a new record for this store.
        Customer customer = null;
        if (!phone.isEmpty()) {
            customer = context.getCustomerService().findByPhone(phone);
            if (customer == null) {
                Customer fresh = new Customer(name, phone, address);
                customer = context.getCustomerService().create(fresh);
            }
        }
        billing.setCustomer(customer);

        PaymentDialog dialog = new PaymentDialog(frame, billing.grandTotal(), symbol());
        dialog.setVisible(true);
        if (!dialog.isConfirmed()) {
            return;
        }
        String cashier = context.getCurrentUser().getDisplayName();
        Invoice invoice = billing.buildInvoice(cashier, dialog.getAmountPaid());
        invoice = context.getInvoiceService().completeSale(invoice);
        billing.clear();
        resetCustomerFields();
        refreshCart();
        reloadCatalog();
        String receipt = context.getReportService().receipt(invoice, settings());
        new TextPreviewDialog(frame, "Invoice " + invoice.getInvoiceNumber(), receipt, 32).setVisible(true);
    }

    private void clearBill() {
        context.getBillingService().clear();
        resetCustomerFields();
        refreshCart();
    }

    private void resetCustomerFields() {
        customerNameField.setText("");
        customerPhoneField.setText("");
        customerAddressField.setText("");
    }

    // ----- shared refresh -----------------------------------------------------

    private void refreshCart() {
        String symbol = symbol();
        cartModel.setRowCount(0);
        int index = 1;
        for (InvoiceItem item : context.getBillingService().getItems()) {
            cartModel.addRow(new Object[] {index++, item.getName(),
                    Money.format(symbol, item.getUnitPrice()), item.getQuantity(),
                    Money.format(symbol, item.getLineTotal())});
        }
        refreshTotals();
    }

    private void refreshTotals() {
        BillingService billing = context.getBillingService();
        String symbol = symbol();
        subtotalLabel.setText("Subtotal:  " + Money.format(symbol, billing.subtotal()));
        discountLabel.setText("Discount:  \u2212" + Money.format(symbol, billing.effectiveDiscount()));
        taxLabel.setText("Tax (" + Money.scale(billing.getTaxPercent()).toPlainString() + "%):  "
                + Money.format(symbol, billing.taxAmount()));
        totalLabel.setText("Grand Total:  " + Money.format(symbol, billing.grandTotal()));
        setSubtitle(billing.totalUnits() + " item(s) in this bill");
    }

    @Override
    public void refreshData() {
        syncing = true;
        discountField.setText(context.getBillingService().getDiscount().toPlainString());
        taxField.setText(Money.scale(context.getBillingService().getTaxPercent()).toPlainString());
        cashierField.setText(context.getCurrentUser().getDisplayName());
        syncing = false;
        reloadCatalog();
        refreshCart();
    }
}
