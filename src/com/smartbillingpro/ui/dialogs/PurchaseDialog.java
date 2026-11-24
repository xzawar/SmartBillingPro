package com.smartbillingpro.ui.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Purchase;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.service.PurchaseService;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Icons;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.PickerDialog;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Record stock bought from a supplier, with full, partial or no payment. */
public final class PurchaseDialog extends FormDialog {

    private final AppContext ctx;
    private final JComboBox<Object> supplier = new JComboBox<>();
    private final JTextField billRef = Ui.field("Supplier's bill / invoice no.");
    private final JTextField find = Ui.search("Scan barcode or search product to add");
    private final List<SellableItem> items = new ArrayList<>();
    private final DefaultTableModel model = new DefaultTableModel(new Object[] {"Product", "Qty", "Unit", "Unit cost",
        "Line total"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1 || column == 3;
        }
    };
    private final JTable table = new JTable(model);
    private final JTextField discount = Ui.money("0.00");
    private final JTextField paid = Ui.money("0.00");
    private final JComboBox<PaymentMethod> method = new JComboBox<>();
    private final JLabel subtotal = Ui.muted(" ");
    private final JLabel total = Ui.heading(" ");
    private final JLabel due = new JLabel(" ");
    private final JTextField note = Ui.field("optional");
    private Purchase saved;

    public PurchaseDialog(Component parent, AppContext ctx, Long supplierId) {
        super(parent, "New purchase", "Save purchase");
        this.ctx = ctx;
        setSubtitle("Stock is added and the supplier's balance is updated when you save.");
        supplier.addItem("No supplier (cash purchase)");
        for (Supplier s : ctx.parties().suppliers(false)) {
            supplier.addItem(s);
            if (supplierId != null && s.id() == supplierId) {
                supplier.setSelectedItem(s);
            }
        }
        for (PaymentMethod m : ctx.settings().paymentMethods(true)) {
            method.addItem(m);
        }
        JButton newSupplier = Ui.icon("plus", "New supplier");
        newSupplier.addActionListener(e -> {
            SupplierDialog d = new SupplierDialog(this, ctx, null);
            if (d.open(560)) {
                Supplier s = ctx.parties().supplier(d.savedId());
                supplier.addItem(s);
                supplier.setSelectedItem(s);
            }
        });
        JPanel supRow = new JPanel(new BorderLayout(4, 0));
        supRow.setOpaque(false);
        supRow.add(supplier, BorderLayout.CENTER);
        supRow.add(newSupplier, BorderLayout.EAST);

        find.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("barcode", 16, Theme.MUTED));
        find.addActionListener(e -> addFromSearch());
        table.setRowHeight(32);
        table.putClientProperty("terminateEditOnFocusLost", true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        model.addTableModelListener(e -> {
            if (e.getColumn() == 1 || e.getColumn() == 3) {
                recalc();
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new Dimension(640, 220));
        JButton remove = Ui.ghost("Remove selected line", "trash");
        remove.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r >= 0) {
                if (table.isEditing()) {
                    table.getCellEditor().cancelCellEditing();
                }
                items.remove(r);
                model.removeRow(r);
                recalc();
            }
        });
        Ui.onChange(discount, this::recalc);
        Ui.onChange(paid, this::recalc);
        supplier.addActionListener(e -> recalc());
        total.setFont(Theme.bold(17));
        due.setFont(Theme.bold(13));
        JButton payAll = Ui.ghost("Paid in full", "check");
        payAll.addActionListener(e -> paid.setText(Money.plain(currentTotal())));
        JPanel payRow = Ui.grid(3, 10, 0, Ui.row("Discount on bill", discount), Ui.row("Paid now", paid),
                Ui.row("Paid by", method));
        setBody(Ui.vbox(12, Ui.grid(2, 12, 0, Ui.row("Supplier", supRow), Ui.row("Bill number", billRef)),
                Ui.row("Add products", find, "Press Enter to add. Edit quantity and cost directly in the table."),
                sp, remove, payRow, Ui.flowLeft(10, payAll), Ui.row("Note", note), Ui.vbox(4, subtotal, total, due)));
        recalc();
    }

    public Purchase saved() {
        return saved;
    }

    private void addFromSearch() {
        String q = find.getText().trim();
        SellableItem item = q.isEmpty() ? null : ctx.inventory().findByCode(q);
        if (item == null) {
            List<SellableItem> hits = ctx.inventory().search(q, 300);
            if (hits.isEmpty()) {
                Msg.info(this, "Not found", "No product matches \"" + q + "\". Add it in Inventory first.");
                return;
            }
            item = hits.size() == 1 ? hits.get(0) : PickerDialog.pick(this, "Choose product", hits,
                    SellableItem::displayName, s -> "Stock " + Qty.format(s.stockQty(), s.unitCode()) + "   •   cost "
                            + Money.format(s.costPrice()), null, null);
        }
        if (item == null) {
            return;
        }
        for (int r = 0; r < items.size(); r++) {
            if (items.get(r).variantId() == item.variantId()) {
                long q2 = Qty.parse(model.getValueAt(r, 1).toString()) + Qty.ONE;
                model.setValueAt(Qty.format(q2), r, 1);
                find.setText("");
                return;
            }
        }
        items.add(item);
        model.addRow(new Object[] {item.displayName(), "1", item.unitCode(), Money.plain(item.costPrice()), ""});
        find.setText("");
        recalc();
        table.setRowSelectionInterval(model.getRowCount() - 1, model.getRowCount() - 1);
    }

    private List<PurchaseService.Line> lines() {
        List<PurchaseService.Line> out = new ArrayList<>();
        for (int r = 0; r < items.size(); r++) {
            try {
                long q = Qty.parse(String.valueOf(model.getValueAt(r, 1)));
                long c = Money.parse(String.valueOf(model.getValueAt(r, 3)));
                out.add(new PurchaseService.Line(items.get(r).variantId(), q, c));
            } catch (IllegalArgumentException e) {
                throw new com.smartbillingpro.db.AppException("Line " + (r + 1) + " (" + items.get(r).displayName() + "): "
                        + e.getMessage());
            }
        }
        return out;
    }

    private long currentTotal() {
        try {
            long sub = lines().stream().mapToLong(PurchaseService::lineTotal).sum();
            return sub - Money.parseOrZero(discount.getText());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private boolean recalcing;

    private void recalc() {
        if (recalcing) {
            return;
        }
        recalcing = true;
        try {
            long sub = 0;
            for (int r = 0; r < items.size(); r++) {
                String lt;
                try {
                    long q = Qty.parse(String.valueOf(model.getValueAt(r, 1)));
                    long c = Money.parse(String.valueOf(model.getValueAt(r, 3)));
                    long line = Money.mulQty(c, q);
                    sub += line;
                    lt = Money.grouped(line);
                } catch (IllegalArgumentException e) {
                    lt = "invalid";
                }
                if (!lt.equals(model.getValueAt(r, 4))) {
                    model.setValueAt(lt, r, 4);
                }
            }
            long disc = Money.parseOrZero(discount.getText());
            long tot = sub - disc;
            long p = Money.parseOrZero(paid.getText());
            subtotal.setText(items.size() + " line(s)   •   subtotal " + Money.format(sub));
            total.setText("Bill total: " + Money.format(tot));
            long d = tot - p;
            boolean noSupplier = !(supplier.getSelectedItem() instanceof Supplier);
            if (d > 0) {
                due.setForeground(noSupplier ? Theme.BAD : Theme.WARN);
                due.setText(noSupplier ? "⚠ Select a supplier to keep " + Money.format(d) + " unpaid"
                        : "Unpaid " + Money.format(d) + " is added to the supplier's balance");
            } else if (d < 0) {
                due.setForeground(Theme.BAD);
                due.setText("⚠ Paid amount is more than the bill total");
            } else {
                due.setForeground(Theme.GOOD);
                due.setText(tot > 0 ? "✓ Fully paid" : " ");
            }
        } finally {
            recalcing = false;
        }
    }

    @Override
    protected boolean onSave() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        PaymentMethod m = (PaymentMethod) method.getSelectedItem();
        long p = Ui.moneyValue(paid, "the amount paid", false);
        Object s = supplier.getSelectedItem();
        saved = ctx.purchases().receive(new PurchaseService.PurchaseForm(s instanceof Supplier sup ? sup.id() : null,
                billRef.getText(), lines(), Ui.moneyValue(discount, "the discount", false), p,
                m == null ? null : m.id(), note.getText()));
        return true;
    }
}
