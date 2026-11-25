package com.smartbillingpro.ui.flows;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.db.AppException;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.service.InventoryService.AdjustMode;
import com.smartbillingpro.ui.OptionList;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.Wizard;
import com.smartbillingpro.util.Qty;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Changes stock by hand in three short questions: which size (only when there
 * are several), what happened, and how many. Every change is recorded with its
 * reason in the stock history.
 */
public final class StockFlow {

    /** One answer to "What happened?". */
    private record Reason(String key, String icon, String title, String subtitle, AdjustMode mode, String reason,
                          String question) {
    }

    private static final List<Reason> REASONS = List.of(
            new Reason("in", "plus", "New stock arrived", "Received without a bill, found, or put back on the shelf",
                    AdjustMode.ADD, "Received without bill", "How many arrived?"),
            new Reason("damaged", "warning", "Damaged or expired", "Can't be sold any more",
                    AdjustMode.REMOVE, "Damaged or expired", "How many were damaged or expired?"),
            new Reason("lost", "close", "Lost or stolen", "Missing from the shelf",
                    AdjustMode.REMOVE, "Lost / theft", "How many are missing?"),
            new Reason("used", "store", "Used in the shop", "Taken for the shop's own use",
                    AdjustMode.REMOVE, "Used in shop", "How many were used?"),
            new Reason("returned", "suppliers", "Sent back to the supplier", "Returned without a credit note",
                    AdjustMode.REMOVE, "Returned to supplier", "How many did you send back?"),
            new Reason("count", "check", "I counted it", "Set the exact number that is on the shelf",
                    AdjustMode.SET, "Stock count correction", "How many are on the shelf now?"));

    private final AppContext ctx;
    private final Product product;
    private final Wizard wizard;
    private Variant variant;
    private Reason reason;
    private boolean saved;

    private StockFlow(Component parent, AppContext ctx, Product product, Long variantId) {
        this.ctx = ctx;
        this.product = product;
        this.wizard = new Wizard(parent, "Change stock – " + product.name());
        List<Variant> active = product.activeVariants();
        variant = active.stream().filter(v -> variantId != null && v.id() == variantId).findFirst()
                .orElse(active.size() == 1 ? active.get(0) : null);
        wizard.add(new VariantStep()).add(new ReasonStep()).add(new AmountStep());
    }

    /** Opens the flow. {@code variantId} pre-selects a size (may be null). Returns true when stock was changed. */
    public static boolean open(Component parent, AppContext ctx, long productId, Long variantId) {
        Product p = ctx.inventory().product(productId);
        if (p == null || p.activeVariants().isEmpty()) {
            return false;
        }
        StockFlow f = new StockFlow(parent, ctx, p, variantId);
        f.wizard.open(false);
        return f.saved;
    }

    private String unit() {
        return product.unitCode();
    }

    private String nameOf(Variant v) {
        return v.isDefault() ? product.name() : v.name();
    }

    private final class VariantStep extends Wizard.Step {
        private OptionList options;
        private final boolean several = product.activeVariants().size() > 1;

        @Override
        public boolean visible() {
            return several;
        }

        @Override
        public String title() {
            return "Which size or type?";
        }

        @Override
        protected JComponent build() {
            List<OptionList.Option> list = new ArrayList<>();
            for (Variant v : product.activeVariants()) {
                list.add(new OptionList.Option(String.valueOf(v.id()), "inventory", nameOf(v),
                        "In stock: " + Qty.format(v.stockQty(), unit())));
            }
            options = new OptionList(list);
            if (variant != null) {
                options.setSelected(String.valueOf(variant.id()));
            }
            options.onPick(k -> wizard.next());
            return options;
        }

        @Override
        public void shown() {
            options.focusSelectedOrFirst();
        }

        @Override
        public void commit() {
            String k = options.selected();
            if (k == null) {
                throw new AppException("Please choose a size or type.");
            }
            variant = product.activeVariants().stream().filter(v -> String.valueOf(v.id()).equals(k)).findFirst()
                    .orElseThrow();
        }
    }

    private final class ReasonStep extends Wizard.Step {
        private OptionList options;

        @Override
        public String title() {
            return "What happened?";
        }

        @Override
        public String help() {
            return "Sales and purchases change stock by themselves. Use this for everything else. "
                    + "Bought stock with a bill? Record it under Suppliers › New purchase instead.";
        }

        @Override
        protected JComponent build() {
            List<OptionList.Option> list = new ArrayList<>();
            for (Reason r : REASONS) {
                list.add(new OptionList.Option(r.key(), r.icon(), r.title(), r.subtitle()));
            }
            options = new OptionList(list);
            options.onPick(k -> wizard.next());
            return options;
        }

        @Override
        public void shown() {
            options.focusSelectedOrFirst();
        }

        @Override
        public void commit() {
            String k = options.selected();
            reason = REASONS.stream().filter(r -> r.key().equals(k)).findFirst().orElse(null);
            if (reason == null) {
                throw new AppException("Please choose what happened.");
            }
        }
    }

    private final class AmountStep extends Wizard.Step {
        private JTextField qty;
        private JTextField note;
        private JLabel preview;

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return reason == null ? "How many?" : reason.question();
        }

        @Override
        public String help() {
            return variant == null ? null : nameOf(variant) + " – in stock now: "
                    + Qty.format(variant.stockQty(), unit());
        }

        @Override
        public String nextLabel() {
            return "Save";
        }

        @Override
        protected JComponent build() {
            qty = Wizard.bigField("0");
            qty.setHorizontalAlignment(SwingConstants.RIGHT);
            qty.addActionListener(e -> wizard.next());
            JPanel amount = new JPanel(new BorderLayout(10, 0));
            amount.setOpaque(false);
            amount.add(qty, BorderLayout.CENTER);
            JLabel suffix = new JLabel(unit());
            suffix.setFont(Theme.font(16));
            suffix.setForeground(Theme.MUTED);
            amount.add(suffix, BorderLayout.EAST);
            preview = new JLabel(" ");
            preview.setFont(Theme.bold(15));
            note = Ui.field("Note (optional), e.g. “box fell”");
            note.addActionListener(e -> wizard.next());
            Ui.onChange(qty, this::updatePreview);
            updatePreview();
            return Ui.vbox(12, amount, preview, Ui.vbox(6, Ui.label("Note"), note));
        }

        private void updatePreview() {
            if (variant == null || reason == null) {
                return;
            }
            long now = variant.stockQty();
            long q;
            try {
                q = qty.getText().isBlank() ? 0 : Qty.parse(qty.getText());
            } catch (IllegalArgumentException e) {
                preview.setForeground(Theme.BAD);
                preview.setText("Please type a number.");
                return;
            }
            long after = switch (reason.mode()) {
                case ADD -> now + q;
                case REMOVE -> now - q;
                case SET -> q;
            };
            if (qty.getText().isBlank()) {
                preview.setForeground(Theme.MUTED);
                preview.setText("Stock now: " + Qty.format(now, unit()));
                return;
            }
            preview.setForeground(after < 0 ? Theme.BAD : Theme.TEXT);
            preview.setText("Stock will be " + Qty.format(after, unit()) + "   (now " + Qty.format(now, unit()) + ")"
                    + (after < 0 ? " – that is more than you have" : ""));
        }

        @Override
        public void shown() {
            qty.requestFocusInWindow();
        }

        @Override
        public void commit() {
            String t = qty.getText().trim();
            if (t.isEmpty()) {
                throw new AppException("Please type how many.");
            }
            long q;
            try {
                q = Qty.parse(t);
            } catch (IllegalArgumentException e) {
                throw new AppException(e.getMessage());
            }
            if (reason.mode() == AdjustMode.REMOVE && q > variant.stockQty() && variant.stockQty() >= 0) {
                throw new AppException("You only have " + Qty.format(variant.stockQty(), unit())
                        + ". If the real number is different, choose “I counted it” instead.");
            }
            ctx.inventory().adjustStock(variant.id(), reason.mode(), q, reason.reason(), note.getText());
            saved = true;
        }
    }
}
