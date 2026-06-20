package com.smartbillingpro.ui.panels;

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
import com.smartbillingpro.ui.dialogs.InvoicePreviewDialog;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.Box;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The point-of-sale screen. The cashier types in a single search box, the
 * matching products appear underneath, and a click adds the item straight into
 * the bill. Quantities are adjusted with the +/\u2212 buttons. The invoice summary
 * and "Generate Invoice" action sit in a card on the right.
 */
public class BillingPanel extends PanelBase {

    // ----- customer information ----------------------------------------------
    private final JTextField customerNameField = new JTextField(18);
    private final JTextField customerPhoneField = new JTextField(18);
    private final JTextField customerAddressField = new JTextField(18);
    private final JTextField cashierField = new JTextField(18);

    // ----- search + live results ---------------------------------------------
    private final JTextField searchField = new JTextField();
    private final DefaultTableModel resultModel = new DefaultTableModel(
            new Object[] {"Product", "SKU", "Price", "Stock"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable resultTable = new StripedTable(resultModel);
    private final List<Product> resultRows = new ArrayList<>();
    private final JLabel resultHint = new JLabel();

    // ----- bill (line items) --------------------------------------------------
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
    private final JLabel invoiceBadge = new JLabel();
    private final JLabel subtotalLabel = new JLabel();
    private final JLabel discountLabel = new JLabel();
    private final JLabel taxLabel = new JLabel();
    private final JLabel totalLabel = new JLabel();

    private boolean syncing = false;

    public BillingPanel(MainFrame frame, AppContext context) {
        super(frame, context, "New Sale",
                "Search an item, click it to add it to the bill, then generate the invoice");

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

    // ----- left column: customer + add items ---------------------------------

    private JComponent buildLeftColumn() {
        JPanel left = new JPanel(new BorderLayout(0, 14));
        left.setOpaque(false);
        left.add(buildCustomerCard(), BorderLayout.NORTH);
        left.add(buildBillCard(), BorderLayout.CENTER);
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

    private void addLabeled(JPanel grid, GridBagConstraints c, int col, int row, String label, JComponent field) {
        JPanel cell = new JPanel(new BorderLayout(0, 4));
        cell.setOpaque(false);
        cell.add(Ui.formLabel(label), BorderLayout.NORTH);
        cell.add(field, BorderLayout.CENTER);
        c.gridx = col;
        c.gridy = row;
        grid.add(cell, c);
    }

    private RoundedPanel buildBillCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        card.add(Ui.title("Add Items", 15), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);

        // --- search box + live results ---
        JPanel searchSection = new JPanel(new BorderLayout(0, 8));
        searchSection.setOpaque(false);

        JPanel searchWrap = new JPanel(new BorderLayout(0, 6));
        searchWrap.setOpaque(false);
        searchWrap.add(Ui.formLabel("Search products by name or SKU"), BorderLayout.NORTH);
        Ui.styleField(searchField);
        searchField.setPreferredSize(new Dimension(0, 40));
        searchField.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                reloadResults();
            }
        });
        searchWrap.add(searchField, BorderLayout.CENTER);
        searchSection.add(searchWrap, BorderLayout.NORTH);

        resultTable.setPreferredScrollableViewportSize(new Dimension(0, 150));
        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                addSelectedResult();
            }
        });
        resultHint.setFont(Theme.regular(12));
        resultHint.setForeground(Theme.MUTED);
        JPanel resultWrap = new JPanel(new BorderLayout(0, 4));
        resultWrap.setOpaque(false);
        resultWrap.add(resultHint, BorderLayout.NORTH);
        resultWrap.add(Ui.scroll(resultTable), BorderLayout.CENTER);
        searchSection.add(resultWrap, BorderLayout.CENTER);

        // --- the bill itself ---
        JPanel billWrap = new JPanel(new BorderLayout(0, 6));
        billWrap.setOpaque(false);
        JPanel billHeader = new JPanel(new BorderLayout(8, 0));
        billHeader.setOpaque(false);
        billHeader.add(Ui.formLabel("Items in this bill (use + / \u2212 to change quantity)"), BorderLayout.WEST);
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
        billHeader.add(lineButtons, BorderLayout.EAST);
        cartTable.setPreferredScrollableViewportSize(new Dimension(0, 170));
        billWrap.add(billHeader, BorderLayout.NORTH);
        billWrap.add(Ui.scroll(cartTable), BorderLayout.CENTER);

        center.add(searchSection, BorderLayout.NORTH);
        center.add(billWrap, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);
        return card;
    }

    // ----- right column: invoice summary -------------------------------------

    private JComponent buildSummaryCard() {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        card.setPreferredSize(new Dimension(330, 0));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.title("Invoice Summary", 15), BorderLayout.WEST);
        invoiceBadge.setFont(Theme.bold(12));
        invoiceBadge.setForeground(Theme.ACCENT);
        head.add(invoiceBadge, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

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

        body.add(Box.createVerticalStrut(8));
        body.add(separator());
        body.add(Box.createVerticalStrut(8));

        subtotalLabel.setFont(Theme.regular(14));
        discountLabel.setFont(Theme.regular(14));
        taxLabel.setFont(Theme.regular(14));
        totalLabel.setFont(Theme.bold(20));
        for (JLabel label : new JLabel[] {subtotalLabel, discountLabel, taxLabel, totalLabel}) {
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setForeground(label == totalLabel ? Theme.PRIMARY : Theme.TEXT);
            body.add(label);
            body.add(Box.createVerticalStrut(6));
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

    // ----- search / bill actions ---------------------------------------------

    private void addSelectedResult() {
        int row = resultTable.getSelectedRow();
        if (row < 0 || row >= resultRows.size()) {
            return;
        }
        try {
            context.getBillingService().addProduct(resultRows.get(row), 1);
            refreshCart();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
    }

    private void reloadResults() {
        String term = searchField.getText().trim();
        List<Product> products = term.isEmpty()
                ? context.getInventoryService().all()
                : context.getInventoryService().search(term);
        String symbol = symbol();
        resultRows.clear();
        resultModel.setRowCount(0);
        for (Product product : products) {
            resultRows.add(product);
            resultModel.addRow(new Object[] {product.getName(), product.getSku(),
                    Money.format(symbol, product.getSalePrice()), product.getQuantity()});
        }
        resultHint.setText(products.isEmpty()
                ? "No products match your search."
                : products.size() + " product(s) found \u2014 click one to add it to the bill");
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

        // Build the invoice in memory only. No confirm box, no database write,
        // and no stock change yet – we go straight to the print preview. The sale
        // is saved (and the customer recorded) ONLY when the user clicks Print.
        String cashier = context.getCurrentUser().getDisplayName();
        Invoice invoice = billing.buildInvoice(cashier, billing.grandTotal());
        invoice.setInvoiceNumber(peekInvoiceNumber());
        invoice.setCreatedAt(com.smartbillingpro.util.Dates.now());

        InvoicePreviewDialog preview = new InvoicePreviewDialog(frame, context, invoice, settings());
        preview.setVisible(true);
        if (preview.isCompleted()) {
            billing.clear();
            resetCustomerFields();
            refreshCart();
            reloadResults();
            updateInvoiceBadge();
        }
    }

    private String peekInvoiceNumber() {
        try {
            return context.getInvoiceService().peekNextInvoiceNumber();
        } catch (RuntimeException ex) {
            return "(pending)";
        }
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

    private void updateInvoiceBadge() {
        try {
            invoiceBadge.setText("Next: " + context.getInvoiceService().peekNextInvoiceNumber());
        } catch (RuntimeException ex) {
            invoiceBadge.setText("");
        }
    }

    @Override
    public void refreshData() {
        syncing = true;
        discountField.setText(context.getBillingService().getDiscount().toPlainString());
        taxField.setText(Money.scale(context.getBillingService().getTaxPercent()).toPlainString());
        cashierField.setText(context.getCurrentUser().getDisplayName());
        syncing = false;
        reloadResults();
        refreshCart();
        updateInvoiceBadge();
    }
}
