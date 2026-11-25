package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SaleItem;
import com.smartbillingpro.service.SaleService;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * Return some or all items of a sale. Stock goes back; the money is refunded in
 * cash or, for account customers, taken off what they owe.
 */
public final class ReturnDialog extends FormDialog {

    private final AppContext ctx;
    private final Sale sale;
    private final List<SaleItem> returnable = new ArrayList<>();
    private final DefaultTableModel model = new DefaultTableModel(new Object[] {"Item", "Sold", "Can return", "Return qty",
        "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 3;
        }
    };
    private final JTable table = new JTable(model);
    private final JTextField reason = Ui.field("e.g. wrong size, damaged, changed mind");
    private final JTextField refund = Ui.money("0.00");
    private final JComboBox<PaymentMethod> method = new JComboBox<>();
    private final JLabel summary = Ui.heading(" ");
    private final JLabel hint = Ui.muted(" ");
    private long value;
    private boolean updating;

    public ReturnDialog(Component parent, AppContext ctx, Sale sale) {
        super(parent, "Return items – " + sale.receiptNo(), "Save return");
        this.ctx = ctx;
        this.sale = sale;
        setSubtitle(sale.customerName() + "  •  total " + Money.format(sale.grandTotal())
                + (sale.returnedTotal() > 0 ? "  •  already returned " + Money.format(sale.returnedTotal()) : ""));
        for (SaleItem i : sale.items()) {
            if (i.returnableQty() > 0) {
                returnable.add(i);
                model.addRow(new Object[] {i.displayName(), Qty.format(i.quantity(), i.unitCode()),
                    Qty.format(i.returnableQty(), i.unitCode()), "0", ""});
            }
        }
        for (PaymentMethod m : ctx.settings().paymentMethods(true)) {
            method.addItem(m);
        }
        if (sale.paymentMethodId() != null) {
            for (int i = 0; i < method.getItemCount(); i++) {
                if (method.getItemAt(i).id() == sale.paymentMethodId()) {
                    method.setSelectedIndex(i);
                }
            }
        }
        table.setRowHeight(32);
        table.putClientProperty("terminateEditOnFocusLost", true);
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        model.addTableModelListener(e -> {
            if (!updating && e.getColumn() == 3) {
                recalc(true);
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new Dimension(560, 180));
        JButton all = Ui.ghost("Return everything", "return");
        all.addActionListener(e -> {
            updating = true;
            for (int r = 0; r < returnable.size(); r++) {
                model.setValueAt(Qty.format(returnable.get(r).returnableQty()), r, 3);
            }
            updating = false;
            recalc(true);
        });
        Ui.onChange(refund, () -> recalc(false));
        summary.setFont(Theme.bold(15));
        setBody(Ui.vbox(12, Ui.muted("Type the quantity being returned for each item."), sp, all,
                Ui.row("Reason *", reason), Ui.grid(2, 12, 0, Ui.row(sale.walkIn() ? "Cash refunded" : "Cash refunded now",
                        refund), Ui.row("Refund method", method)), summary, hint));
        refund.setEditable(!sale.walkIn());
        recalc(true);
    }

    private List<SaleService.ReturnLine> lines() {
        List<SaleService.ReturnLine> out = new ArrayList<>();
        for (int r = 0; r < returnable.size(); r++) {
            String t = String.valueOf(model.getValueAt(r, 3)).trim();
            long q = t.isEmpty() ? 0 : Qty.parse(t);
            if (q > 0) {
                out.add(new SaleService.ReturnLine(returnable.get(r).id(), q));
            }
        }
        return out;
    }

    private void recalc(boolean resetRefund) {
        if (updating) {
            return;
        }
        updating = true;
        try {
            value = 0;
            boolean invalid = false;
            for (int r = 0; r < returnable.size(); r++) {
                SaleItem item = returnable.get(r);
                try {
                    String t = String.valueOf(model.getValueAt(r, 3)).trim();
                    long q = t.isEmpty() ? 0 : Qty.parse(t);
                    if (q < 0 || q > item.returnableQty()) {
                        throw new IllegalArgumentException();
                    }
                    long v = q == 0 ? 0 : SaleService.returnValue(item, q)[2];
                    value += v;
                    model.setValueAt(q == 0 ? "" : Money.grouped(v), r, 4);
                } catch (IllegalArgumentException e) {
                    model.setValueAt("check qty", r, 4);
                    invalid = true;
                }
            }
            long maxCash = ctx.sales().maxCashRefund(sale, value);
            if (resetRefund) {
                refund.setText(Money.plain(maxCash));
            }
            long cash = Money.parseOrZero(refund.getText());
            summary.setForeground(invalid ? Theme.BAD : Theme.TEXT);
            summary.setText(invalid ? "Check the quantities" : "Return value: " + Money.format(value));
            if (sale.walkIn()) {
                hint.setText("Refund the full value in cash to the customer.");
            } else {
                long credit = Math.max(0, value - cash);
                hint.setText("Cash back: " + Money.format(cash) + "   •   taken off " + sale.customerName()
                        + "'s balance: " + Money.format(credit) + "   (max cash " + Money.format(maxCash) + ")");
            }
        } finally {
            updating = false;
        }
    }

    @Override
    protected boolean onSave() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        List<SaleService.ReturnLine> l;
        try {
            l = lines();
        } catch (IllegalArgumentException e) {
            throw new com.smartbillingpro.db.AppException("A return quantity is not a valid number.");
        }
        long cash = Ui.moneyValue(refund, "the refund amount", false);
        PaymentMethod m = (PaymentMethod) method.getSelectedItem();
        ctx.sales().processReturn(sale.id(), l, cash, m == null ? null : m.id(), reason.getText());
        return true;
    }
}
