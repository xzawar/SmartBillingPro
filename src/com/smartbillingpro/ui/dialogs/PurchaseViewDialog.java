package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Purchase;
import com.smartbillingpro.model.PurchaseItem;
import com.smartbillingpro.ui.Detail;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/** Shows one purchase: what was bought, the total and what was paid. Can cancel (reverse) it. */
public final class PurchaseViewDialog extends FormDialog {

    private boolean reversed;

    public PurchaseViewDialog(Component parent, AppContext ctx, long purchaseId) {
        this(parent, ctx, ctx.purchases().find(purchaseId));
    }

    private PurchaseViewDialog(Component parent, AppContext ctx, Purchase p) {
        super(parent, "Purchase " + p.purchaseNo(), "Close");
        cancelButton.setVisible(false);
        setSubtitle(Dates.formatDateTime(p.createdAt()) + "   •   "
                + (p.supplierName() == null ? "Not from a saved supplier" : p.supplierName())
                + (Text.blank(p.billRef()) ? "" : "   •   bill " + p.billRef()));
        List<JComponent> rows = new ArrayList<>();
        if (p.reversed()) {
            rows.add(Ui.pill("Cancelled – " + Text.orDash(p.reversalReason()), Ui.Tone.NEUTRAL));
        }
        for (PurchaseItem i : p.items()) {
            JLabel amt = new JLabel(Money.format(i.lineTotal()));
            amt.setFont(Theme.bold(14));
            rows.add(Detail.line(i.displayName(), Qty.format(i.quantity(), i.unitCode()) + " × "
                    + Money.format(i.unitCost()), amt));
        }
        if (p.discount() > 0) {
            rows.add(total("Discount", "−" + Money.format(p.discount()), false));
        }
        rows.add(total("Total", Money.format(p.total()), true));
        rows.add(total("Paid" + (p.paymentMethodName() == null || p.amountPaid() == 0 ? ""
                : " (" + p.paymentMethodName() + ")"), Money.format(p.amountPaid()), false));
        if (p.balanceDue() > 0) {
            JComponent due = total("Still owed to the supplier", Money.format(p.balanceDue()), false);
            rows.add(due);
        }
        if (!Text.blank(p.note())) {
            rows.add(Ui.muted("Note: " + p.note()));
        }
        setBody(Ui.vbox(6, rows.toArray(new JComponent[0])));
        if (!p.reversed()) {
            JButton reverse = Ui.ghost("Cancel this purchase…", "return");
            reverse.setToolTipText("Takes the stock back out and removes the bill from the supplier's account");
            reverse.addActionListener(e -> {
                String reason = Msg.ask(this, "Cancel purchase " + p.purchaseNo(),
                        "The stock it added is taken back out and the bill is removed from the supplier's account. "
                                + "The purchase stays in your history, marked as cancelled.\n\nWhy is it being cancelled?", "");
                if (reason != null) {
                    try {
                        ctx.purchases().reverse(p.id(), reason);
                        reversed = true;
                        markSaved();
                        dispose();
                    } catch (RuntimeException ex) {
                        showError(ex.getMessage());
                    }
                }
            });
            addFooterLeft(reverse);
        }
    }

    private static JComponent total(String label, String value, boolean big) {
        JLabel v = new JLabel(value);
        v.setFont(Theme.bold(big ? 17 : 14));
        JLabel l = new JLabel(label);
        l.setFont(big ? Theme.bold(15) : Theme.font(14));
        l.setForeground(big ? Theme.TEXT : Theme.TEXT_2);
        javax.swing.JPanel p = new javax.swing.JPanel(new java.awt.BorderLayout());
        p.setOpaque(false);
        p.add(l, java.awt.BorderLayout.WEST);
        p.add(v, java.awt.BorderLayout.EAST);
        p.setBorder(javax.swing.BorderFactory.createEmptyBorder(big ? 8 : 2, 0, 0, 0));
        return p;
    }

    @Override
    protected boolean onSave() {
        return true;
    }

    /** True when the purchase was cancelled from this dialog. */
    public boolean wasReversed() {
        return reversed;
    }
}
