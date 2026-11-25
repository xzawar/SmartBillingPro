package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.service.Cart;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Component;

/** Change the quantity, price or discount of one cart line. */
public final class LineEditDialog extends FormDialog {

    private final Cart cart;
    private final Cart.Line line;
    private final JTextField qty = Ui.field();
    private final JTextField price = Ui.money("");
    private final JTextField discount = Ui.money("0.00");
    private final JLabel total = Ui.heading(" ");

    public LineEditDialog(Component parent, Cart cart, Cart.Line line) {
        super(parent, line.item().displayName(), "Update");
        this.cart = cart;
        this.line = line;
        setSubtitle("In stock: " + Qty.format(line.item().stockQty(), line.item().unitCode())
                + "   •   List price " + Money.format(line.item().salePrice()));
        qty.setText(Qty.format(line.qty()));
        price.setText(Money.plain(line.unitPrice()));
        discount.setText(line.lineDiscount() == 0 ? "" : Money.plain(line.lineDiscount()));
        Ui.onChange(qty, this::preview);
        Ui.onChange(price, this::preview);
        Ui.onChange(discount, this::preview);
        setBody(Ui.vbox(12, Ui.row("Quantity (" + line.item().unitCode() + ")", qty,
                line.item().fractional() ? "Decimals allowed, e.g. 0.25" : "Whole numbers only. Enter 0 to remove."),
                Ui.row("Unit price", price), Ui.row("Discount on this line (amount)", discount), total));
        preview();
        qty.selectAll();
    }

    private void preview() {
        try {
            long q = Qty.parse(qty.getText());
            long p = Money.parse(price.getText());
            long d = discount.getText().isBlank() ? 0 : Money.parse(discount.getText());
            total.setText("Line amount: " + Money.format(Money.mulQty(p, q) - d));
        } catch (IllegalArgumentException e) {
            total.setText(" ");
        }
    }

    @Override
    protected boolean onSave() {
        long q = Ui.qtyValue(qty, "the quantity", true);
        long p = Ui.moneyValue(price, "the price", true);
        long d = Ui.moneyValue(discount, "the discount", false);
        if (q == 0) {
            cart.remove(line);
            return true;
        }
        long oldQ = line.qty();
        long oldP = line.unitPrice();
        try {
            cart.setQty(line, q);
            cart.setUnitPrice(line, p);
            cart.setLineDiscount(line, d);
        } catch (RuntimeException e) {
            cart.setQty(line, oldQ);
            cart.setUnitPrice(line, oldP);
            throw e;
        }
        return true;
    }
}
