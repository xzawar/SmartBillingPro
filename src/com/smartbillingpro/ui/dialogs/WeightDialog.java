package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.model.Unit;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Asks how much of a weighed / measured product is sold. The cashier types the
 * weight in any unit of the same kind (e.g. 250 g for a product priced per kg),
 * or the money amount the customer wants; the other value is calculated.
 */
public final class WeightDialog extends FormDialog {

    private final SellableItem item;
    private final JTextField amount = Ui.field();
    private final JComboBox<Unit> unit = new JComboBox<>();
    private final JTextField money = Ui.money("or enter amount in money");
    private final JLabel result = new JLabel(" ");
    private final BigDecimal productFactor;
    private Long qty;
    private boolean syncing;

    private WeightDialog(Component parent, AppContext ctx, SellableItem item) {
        super(parent, item.displayName(), "Add to bill");
        this.item = item;
        this.productFactor = item.unitFactor();
        setSubtitle("Price " + Money.format(item.salePrice()) + " per " + item.unitCode() + "   •   In stock "
                + Qty.format(item.stockQty(), item.unitCode()));
        List<Unit> same = ctx.inventory().units(true).stream().filter(u -> u.dimension() == item.dimension()).toList();
        Unit preferred = null;
        for (Unit u : same) {
            unit.addItem(u);
            if (u.id() == item.unitId()) {
                preferred = u;
            }
        }
        // For kg-priced goods cashiers usually weigh in grams
        for (Unit u : same) {
            if (item.unitCode().equalsIgnoreCase("kg") && u.code().equalsIgnoreCase("g")) {
                preferred = u;
            }
            if (item.unitCode().equalsIgnoreCase("L") && u.code().equalsIgnoreCase("ml")) {
                preferred = u;
            }
        }
        unit.setSelectedItem(preferred);
        unit.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, v instanceof Unit u ? u.code() : v, i, s, f);
            }
        });
        amount.setHorizontalAlignment(JTextField.RIGHT);
        amount.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "font:+6");
        money.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "font:+2");
        result.setFont(Theme.bold(16));
        Ui.onChange(amount, this::fromAmount);
        Ui.onChange(money, this::fromMoney);
        unit.addActionListener(e -> fromAmount());
        javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(amount, java.awt.BorderLayout.CENTER);
        row.add(unit, java.awt.BorderLayout.EAST);
        setBody(Ui.vbox(12, Ui.row("Weight / quantity", row), Ui.row("Amount (optional)", money), result));
    }

    private Unit selectedUnit() {
        return (Unit) unit.getSelectedItem();
    }

    private void fromAmount() {
        if (syncing) {
            return;
        }
        try {
            long entered = Qty.parse(amount.getText());
            qty = InventoryService.convert(entered, selectedUnit(), productFactor);
            show(qty);
            syncing = true;
            money.setText(qty > 0 ? Money.plain(Money.mulQty(item.salePrice(), qty)) : "");
            syncing = false;
        } catch (IllegalArgumentException e) {
            qty = null;
            result.setText(" ");
        }
    }

    private void fromMoney() {
        if (syncing || money.getText().isBlank()) {
            return;
        }
        try {
            long m = Money.parse(money.getText());
            if (item.salePrice() <= 0) {
                return;
            }
            BigDecimal q = BigDecimal.valueOf(m).divide(BigDecimal.valueOf(item.salePrice()), 3, RoundingMode.DOWN);
            qty = Qty.fromDecimal(q);
            Unit u = selectedUnit();
            long inSelected = Qty.fromDecimal(Qty.toDecimal(qty).multiply(productFactor)
                    .divide(u.factor(), 3, RoundingMode.HALF_UP));
            syncing = true;
            amount.setText(Qty.format(inSelected));
            syncing = false;
            show(qty);
        } catch (IllegalArgumentException | ArithmeticException e) {
            qty = null;
            result.setText(" ");
        }
    }

    private void show(long q) {
        result.setForeground(q > item.stockQty() ? Theme.BAD : Theme.TEXT);
        result.setText(Qty.format(q, item.unitCode()) + "  ×  " + Money.format(item.salePrice()) + "  =  "
                + Money.format(Money.mulQty(item.salePrice(), q)) + (q > item.stockQty() ? "   (more than in stock)" : ""));
    }

    @Override
    protected boolean onSave() {
        if (qty == null || qty <= 0) {
            Ui.setError(amount, true);
            amount.requestFocusInWindow();
            throw new com.smartbillingpro.db.AppException("Enter the weight or quantity (at least 0.001 "
                    + item.unitCode() + ").");
        }
        return true;
    }

    /** Returns the quantity in thousandths of the product's unit, or null when cancelled. */
    public static Long ask(Component parent, AppContext ctx, SellableItem item) {
        WeightDialog d = new WeightDialog(parent, ctx, item);
        return d.open(440) ? d.qty : null;
    }
}
