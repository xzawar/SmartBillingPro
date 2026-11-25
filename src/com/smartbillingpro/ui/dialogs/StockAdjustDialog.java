package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Qty;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Component;

/** Add, remove or set stock by hand - always with a reason, always recorded. */
public final class StockAdjustDialog extends FormDialog {

    private final AppContext ctx;
    private final Product product;
    private final JComboBox<Variant> variant = new JComboBox<>();
    private final JComboBox<InventoryService.AdjustMode> mode = new JComboBox<>(InventoryService.AdjustMode.values());
    private final JTextField qty = Ui.field("0");
    private final JComboBox<String> reason = new JComboBox<>(InventoryService.ADJUST_REASONS);
    private final JTextField note = Ui.field("optional details");
    private final JLabel current = Ui.muted(" ");
    private final JLabel after = Ui.heading(" ");

    public StockAdjustDialog(Component parent, AppContext ctx, Product product) {
        super(parent, "Adjust stock – " + product.name(), "Save adjustment");
        this.ctx = ctx;
        this.product = product;
        setSubtitle("Use this for damage, losses, counting corrections or stock received without a bill. "
                + "Purchases from suppliers are recorded in Suppliers › Purchases.");
        for (Variant v : product.activeVariants()) {
            variant.addItem(v);
        }
        variant.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean s, boolean f) {
                Object text = v instanceof Variant x ? (x.isDefault() ? product.name() : x.name()) + "   (stock "
                        + Qty.format(x.stockQty(), product.unitCode()) + ")" : v;
                return super.getListCellRendererComponent(l, text, i, s, f);
            }
        });
        reason.setEditable(true);
        variant.addActionListener(e -> preview());
        mode.addActionListener(e -> preview());
        Ui.onChange(qty, this::preview);
        after.setFont(Theme.bold(15));
        setBody(Ui.vbox(12, product.hasVariants() ? Ui.row("Variation", variant) : current,
                Ui.grid(2, 12, 0, Ui.row("Change", mode), Ui.row("Quantity (" + product.unitCode() + ")", qty)),
                Ui.grid(2, 12, 0, Ui.row("Reason *", reason), Ui.row("Note", note)), after));
        preview();
    }

    private void preview() {
        Variant v = (Variant) variant.getSelectedItem();
        if (v == null) {
            return;
        }
        current.setText("Current stock: " + Qty.format(v.stockQty(), product.unitCode()));
        try {
            long q = Qty.parse(qty.getText());
            long result = switch ((InventoryService.AdjustMode) mode.getSelectedItem()) {
                case ADD -> v.stockQty() + q;
                case REMOVE -> v.stockQty() - q;
                case SET -> q;
            };
            after.setForeground(result < 0 ? Theme.BAD : Theme.TEXT);
            after.setText("Stock after: " + Qty.format(result, product.unitCode())
                    + (result < 0 ? "  (not enough stock)" : "") + "   (now " + Qty.format(v.stockQty(), product.unitCode()) + ")");
        } catch (IllegalArgumentException e) {
            after.setText(" ");
        }
    }

    @Override
    protected boolean onSave() {
        Variant v = (Variant) variant.getSelectedItem();
        long q = Ui.qtyValue(qty, "the quantity", true);
        Object r = reason.getSelectedItem();
        ctx.inventory().adjustStock(v.id(), (InventoryService.AdjustMode) mode.getSelectedItem(), q,
                r == null ? "" : r.toString(), note.getText());
        return true;
    }
}
