package com.smartbillingpro.ui.flows;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.db.AppException;
import com.smartbillingpro.model.Category;
import com.smartbillingpro.model.Dimension;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.model.Unit;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.ui.Collapsible;
import com.smartbillingpro.ui.Icons;
import com.smartbillingpro.ui.OptionList;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.Wizard;
import com.smartbillingpro.ui.WrapLayout;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adds or edits a product one question at a time:
 * name, how it is sold, sizes, barcode, price, cost, stock and category, then a
 * review screen. Rarely needed details (SKU, description, supplier, picture,
 * pack size, low-stock warning) are only offered from the review screen.
 */
public final class ProductFlow {

    /** What happened when the flow closed. */
    public record Result(Long productId, boolean addAnother) {
    }

    /** One size / type being edited. A simple product has one unnamed draft. */
    private static final class VDraft {
        Long id;
        String name = "";
        String barcode = "";
        String sku = "";
        long price = -1;
        long cost;
        long stock;
        long current;
    }

    private final AppContext ctx;
    private final Product existing;
    private final Wizard wizard;

    private String name = "";
    private Unit unit;
    private boolean hasVariants;
    private List<VDraft> variants = new ArrayList<>();
    private Long categoryId;
    private String categoryName;
    private String description;
    private Long supplierId;
    private String supplierName;
    private byte[] image;
    private boolean imageChanged;
    private String packValue;
    private String packUnit;
    private long minStock;
    private boolean costWarned;
    private Long savedId;
    private boolean addAnother;

    private final Wizard.Step nameStep = new NameStep();
    private final Wizard.Step soldStep = new SoldStep();
    private final Wizard.Step sizesStep = new SizesStep();
    private final Wizard.Step sizeNamesStep = new SizeNamesStep();
    private final Wizard.Step barcodeStep = new BarcodeStep();
    private final Wizard.Step priceStep = new PriceStep();
    private final Wizard.Step costStep = new CostStep();
    private final Wizard.Step stockStep = new StockStep();
    private final Wizard.Step categoryStep = new CategoryStep();
    private final Wizard.Step lowStockStep = new LowStockStep();
    private final Wizard.Step skuStep = new SkuStep();
    private final Wizard.Step descriptionStep = new DescriptionStep();
    private final Wizard.Step supplierStep = new SupplierStep();
    private final Wizard.Step pictureStep = new PictureStep();
    private final Wizard.Step packStep = new PackStep();

    private ProductFlow(Component parent, AppContext ctx, Product existing, String barcode, String prefillName) {
        this.ctx = ctx;
        this.existing = existing;
        this.wizard = new Wizard(parent, existing == null ? "Add a product" : "Edit product");
        minStock = Qty.of(ctx.settings().get().lowStockDefault());
        if (existing == null) {
            VDraft v = new VDraft();
            v.barcode = barcode == null ? "" : barcode.trim();
            variants.add(v);
            name = prefillName == null ? "" : prefillName.trim();
        } else {
            load(existing);
        }
        wizard.add(nameStep).add(soldStep).add(sizesStep).add(sizeNamesStep).add(barcodeStep).add(priceStep)
                .add(costStep).add(stockStep).add(categoryStep)
                .add(lowStockStep).add(skuStep).add(descriptionStep).add(supplierStep).add(pictureStep).add(packStep)
                .review(new ReviewStep());
    }

    /** Ask for a new product. {@code barcode} / {@code name} pre-fill the answers (may be null). */
    public static Result add(Component parent, AppContext ctx, String barcode, String name) {
        ProductFlow f = new ProductFlow(parent, ctx, null, barcode, name);
        boolean done = f.wizard.open(false);
        return new Result(done ? f.savedId : null, done && f.addAnother);
    }

    /** Edit a product, starting on its summary. Returns true when saved. */
    public static boolean edit(Component parent, AppContext ctx, long productId) {
        Product p = ctx.inventory().product(productId);
        if (p == null) {
            return false;
        }
        ProductFlow f = new ProductFlow(parent, ctx, p, null, null);
        return f.wizard.open(true) && f.savedId != null;
    }

    private void load(Product p) {
        name = p.name();
        unit = ctx.inventory().unit(p.unitId());
        hasVariants = p.hasVariants();
        for (Variant v : p.activeVariants()) {
            VDraft d = new VDraft();
            d.id = v.id();
            d.name = v.isDefault() ? "" : v.name();
            d.barcode = Text.trim(v.barcode());
            d.sku = Text.trim(v.sku());
            d.price = v.salePrice();
            d.cost = v.costPrice();
            d.current = v.stockQty();
            variants.add(d);
        }
        if (variants.isEmpty()) {
            variants.add(new VDraft());
        }
        if (!p.activeVariants().isEmpty()) {
            minStock = p.activeVariants().get(0).minStock();
        }
        categoryId = p.categoryId();
        categoryName = p.categoryName();
        description = p.description();
        supplierId = p.supplierId();
        supplierName = p.supplierName();
        image = p.hasImage() ? ctx.inventory().image(p.id()) : null;
        packValue = p.weightValue();
        packUnit = p.weightUnit();
    }

    // ------------------------------------------------------------ helpers

    private String unitCode() {
        return unit == null ? "pcs" : unit.code();
    }

    private boolean fractional() {
        return unit != null && unit.fractional();
    }

    /** "per kg" / "each". */
    private String per() {
        return fractional() ? "per " + unitCode() : "each";
    }

    private String soldByText() {
        if (unit == null) {
            return "";
        }
        if (unit.dimension() == Dimension.COUNT && "pcs".equalsIgnoreCase(unit.code())) {
            return "By the piece";
        }
        String kind = switch (unit.dimension()) {
            case WEIGHT -> "By weight";
            case VOLUME -> "By volume";
            case LENGTH -> "By length";
            default -> "In " + unit.name().toLowerCase(Locale.ROOT);
        };
        return kind + " (price per " + unit.code() + ")";
    }

    private String label(VDraft v) {
        return hasVariants ? v.name : name;
    }

    private static JTextField enterGoesTo(JTextField f, Runnable r) {
        f.addActionListener(e -> r.run());
        return f;
    }

    /** One labelled box per size (used for barcodes, prices, costs, stock). */
    private List<JTextField> rows(JPanel into, List<VDraft> list, Function<VDraft, String> value,
                                  Function<VDraft, JTextField> factory, List<JLabel> statuses) {
        List<JTextField> fields = new ArrayList<>();
        into.setLayout(new javax.swing.BoxLayout(into, javax.swing.BoxLayout.Y_AXIS));
        into.setOpaque(false);
        for (int i = 0; i < list.size(); i++) {
            VDraft v = list.get(i);
            JPanel row = new JPanel(new BorderLayout(12, 2));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            JLabel l = new JLabel(v.name.isBlank() ? name : v.name);
            l.setFont(Theme.bold(15));
            l.setPreferredSize(new java.awt.Dimension(150, 36));
            JTextField f = factory.apply(v);
            f.setText(value.apply(v));
            f.setFont(Theme.font(16));
            row.add(l, BorderLayout.WEST);
            row.add(f, BorderLayout.CENTER);
            if (statuses != null) {
                JLabel st = new JLabel(" ");
                st.setFont(Theme.font(12));
                statuses.add(st);
                row.add(st, BorderLayout.SOUTH);
            }
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            into.add(row);
            fields.add(f);
        }
        for (int i = 0; i < fields.size(); i++) {
            final int idx = i;
            fields.get(i).addActionListener(e -> {
                if (idx < fields.size() - 1) {
                    fields.get(idx + 1).requestFocusInWindow();
                } else {
                    wizard.next();
                }
            });
        }
        return fields;
    }

    private static void focusFirstEmpty(List<JTextField> fields) {
        for (JTextField f : fields) {
            if (f.getText().isBlank()) {
                f.requestFocusInWindow();
                return;
            }
        }
        if (!fields.isEmpty()) {
            fields.get(0).requestFocusInWindow();
            fields.get(0).selectAll();
        }
    }

    private static JPanel withSuffix(JTextField field, String suffix) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.add(field, BorderLayout.CENTER);
        JLabel s = new JLabel(suffix);
        s.setFont(Theme.font(16));
        s.setForeground(Theme.MUTED);
        p.add(s, BorderLayout.EAST);
        return p;
    }

    // ------------------------------------------------------------ steps

    private final class NameStep extends Wizard.Step {
        private JTextField field;

        @Override
        public String title() {
            return "What is the product called?";
        }

        @Override
        public String help() {
            return "Use the name you would say to a customer, e.g. “Coca Cola 1.5L” or “Basmati rice”.";
        }

        @Override
        protected JComponent build() {
            field = enterGoesTo(Wizard.bigField("Product name"), () -> wizard.next());
            field.setText(name);
            return field;
        }

        @Override
        public void shown() {
            field.requestFocusInWindow();
            field.selectAll();
        }

        @Override
        public void commit() {
            String n = field.getText().trim();
            if (n.isEmpty()) {
                throw new AppException("Please type the product's name.");
            }
            if (n.length() > 120) {
                throw new AppException("That name is too long. Please keep it under 120 letters.");
            }
            name = n;
        }
    }

    private final class SoldStep extends Wizard.Step {
        private OptionList options;
        private JComboBox<Unit> other;

        @Override
        public String title() {
            return "How do you sell " + (name.isBlank() ? "it" : name) + "?";
        }

        @Override
        public String help() {
            return "This decides how the price works.";
        }

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        protected JComponent build() {
            List<OptionList.Option> list = new ArrayList<>();
            list.add(new OptionList.Option("pcs", "inventory", "By the piece", "Packets, bottles, boxes — counted one by one"));
            list.add(new OptionList.Option("kg", "scale", "By weight", "Priced per kg, e.g. rice, sugar, sweets"));
            list.add(new OptionList.Option("L", "drop", "By volume", "Priced per litre, e.g. loose milk, oil"));
            list.add(new OptionList.Option("m", "ruler", "By length", "Priced per metre, e.g. cloth, wire"));
            for (Unit u : ctx.inventory().units(true)) {
                if (!u.builtIn()) {
                    list.add(new OptionList.Option("u:" + u.id(), "tag", "In " + u.name().toLowerCase(Locale.ROOT),
                            "Priced per " + u.code()));
                }
            }
            options = new OptionList(list);
            if (unit != null) {
                options.setSelected(unit.builtIn() ? unit.code() : "u:" + unit.id());
            }
            options.onPick(key -> {
                choose(key);
                wizard.next();
            });
            other = new JComboBox<>();
            for (Unit u : ctx.inventory().units(true)) {
                other.addItem(u);
            }
            if (unit != null) {
                other.setSelectedItem(unit);
            }
            JButton use = Ui.secondary("Use this unit", null);
            use.addActionListener(e -> {
                Unit u = (Unit) other.getSelectedItem();
                if (u != null) {
                    unit = u;
                    options.setSelected(null);
                    wizard.next();
                }
            });
            JPanel otherRow = Ui.flowLeft(8, other, use);
            Collapsible more = new Collapsible("Use a different unit (g, ml, dozen…)", otherRow);
            JPanel p = new JPanel(new BorderLayout(0, 14));
            p.setOpaque(false);
            p.add(options, BorderLayout.NORTH);
            p.add(more, BorderLayout.CENTER);
            return p;
        }

        private void choose(String key) {
            if (key.startsWith("u:")) {
                unit = ctx.inventory().unit(Long.parseLong(key.substring(2)));
            } else {
                unit = ctx.inventory().unitByCode(key);
            }
        }

        @Override
        public void shown() {
            options.focusSelectedOrFirst();
        }

        @Override
        public void commit() {
            if (options.selected() != null) {
                choose(options.selected());
            }
            if (unit == null) {
                throw new AppException("Please choose how you sell it.");
            }
        }
    }

    private final class SizesStep extends Wizard.Step {
        private OptionList options;

        @Override
        public String title() {
            return "Does it come in different sizes or types?";
        }

        @Override
        public String help() {
            return "For example a T-shirt in Small, Medium and Large, or a drink in 500 ml and 1.5 L. "
                    + "Each size gets its own price, barcode and stock.";
        }

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        protected JComponent build() {
            options = new OptionList(List.of(
                    new OptionList.Option("no", "check", "No, just one", "One price and one barcode"),
                    new OptionList.Option("yes", "tag", "Yes, it has sizes or types", "e.g. Small / Medium / Large")));
            options.setSelected(hasVariants ? "yes" : "no");
            options.onPick(k -> wizard.next());
            return options;
        }

        @Override
        public void shown() {
            options.focusSelectedOrFirst();
        }

        @Override
        public void commit() {
            boolean yes = "yes".equals(options.selected());
            if (!yes && hasVariants && variants.size() > 1) {
                // keep the first size as the product; others are hidden when saved (history is kept)
                variants = new ArrayList<>(variants.subList(0, 1));
                variants.get(0).name = "";
            }
            if (!yes) {
                variants.get(0).name = "";
            }
            hasVariants = yes;
        }

        @Override
        public Wizard.Step then() {
            return hasVariants ? sizeNamesStep : null;
        }
    }

    private final class SizeNamesStep extends Wizard.Step {
        private final List<String> names = new ArrayList<>();
        private JPanel chips;
        private JTextField input;

        @Override
        public boolean visible() {
            return hasVariants;
        }

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return "What are the sizes or types?";
        }

        @Override
        public String help() {
            return "Tap a suggestion, or type your own and press Enter. Add at least two.";
        }

        @Override
        protected JComponent build() {
            names.clear();
            for (VDraft v : variants) {
                if (!v.name.isBlank()) {
                    names.add(v.name);
                }
            }
            chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
            chips.setOpaque(false);
            input = Wizard.bigField("Type a size or type, e.g. 32, Red, Family pack");
            input.setFont(Theme.font(16));
            input.addActionListener(e -> {
                String t = input.getText().trim();
                if (t.isEmpty()) {
                    wizard.next();
                    return;
                }
                addName(t);
                input.setText("");
            });
            JButton add = Ui.secondary("Add", "plus");
            add.addActionListener(e -> {
                addName(input.getText().trim());
                input.setText("");
                input.requestFocusInWindow();
            });
            JPanel inputRow = new JPanel(new BorderLayout(8, 0));
            inputRow.setOpaque(false);
            inputRow.add(input, BorderLayout.CENTER);
            inputRow.add(add, BorderLayout.EAST);
            JPanel suggestions = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
            suggestions.setOpaque(false);
            for (String s : suggestionsFor()) {
                JButton b = Ui.ghost("+ " + s, null);
                b.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:#ffffff;borderColor:"
                        + Theme.hex(Theme.BORDER_STRONG) + ";borderWidth:1;margin:5,12,5,12");
                b.addActionListener(e -> addName(s));
                suggestions.add(b);
            }
            refreshChips();
            return Ui.vbox(14, inputRow, Ui.label("Suggestions"), suggestions, Ui.label("Added"), chips);
        }

        private List<String> suggestionsFor() {
            if (unit != null && unit.dimension() == Dimension.VOLUME) {
                return List.of("250 ml", "500 ml", "1 L", "1.5 L", "2 L");
            }
            if (unit != null && unit.dimension() == Dimension.WEIGHT) {
                return List.of("250 g", "500 g", "1 kg", "5 kg");
            }
            return List.of("Small", "Medium", "Large", "Extra Large");
        }

        private void addName(String n) {
            if (n == null || n.isBlank()) {
                return;
            }
            if (names.stream().anyMatch(x -> x.equalsIgnoreCase(n))) {
                wizard.showError("“" + n + "” is already added.");
                return;
            }
            if (n.length() > 40) {
                wizard.showError("Please keep size names short (under 40 letters).");
                return;
            }
            wizard.clearError();
            names.add(n);
            wizard.touch();
            refreshChips();
        }

        private void refreshChips() {
            chips.removeAll();
            if (names.isEmpty()) {
                chips.add(Ui.muted("Nothing added yet."));
            }
            for (String n : names) {
                JButton chip = new JButton(n, Icons.get("close", 12, Theme.ACCENT_INK));
                chip.setHorizontalTextPosition(SwingConstants.LEFT);
                chip.setIconTextGap(8);
                chip.setToolTipText("Remove " + n);
                chip.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:" + Theme.hex(Theme.ACCENT_SOFT)
                        + ";foreground:" + Theme.hex(Theme.ACCENT_INK) + ";borderWidth:0;focusWidth:0;margin:6,12,6,10");
                chip.setFont(Theme.bold(13));
                chip.addActionListener(e -> {
                    names.remove(n);
                    refreshChips();
                });
                chips.add(chip);
            }
            chips.revalidate();
            chips.repaint();
        }

        @Override
        public void shown() {
            input.requestFocusInWindow();
        }

        @Override
        public void commit() {
            String pending = input.getText().trim();
            if (!pending.isEmpty()) {
                addName(pending);
                input.setText("");
            }
            if (names.size() < 2) {
                throw new AppException("Add at least two sizes or types — or go back and choose “No, just one”.");
            }
            List<VDraft> old = new ArrayList<>(variants);
            List<VDraft> next = new ArrayList<>();
            for (String n : names) {
                VDraft match = old.stream().filter(v -> v.name.equalsIgnoreCase(n)).findFirst().orElse(null);
                if (match == null) {
                    match = old.stream().filter(v -> v.name.isBlank()).findFirst().orElse(null);
                }
                if (match == null) {
                    match = new VDraft();
                    if (!next.isEmpty()) {
                        match.price = next.get(next.size() - 1).price;
                        match.cost = next.get(next.size() - 1).cost;
                    }
                }
                old.remove(match);
                match.name = n;
                next.add(match);
            }
            variants = next;
        }

        @Override
        public Wizard.Step then() {
            if (variants.stream().anyMatch(v -> v.price < 0)) {
                return priceStep;
            }
            return null;
        }
    }

    private final class BarcodeStep extends Wizard.Step {
        private final List<JTextField> fields = new ArrayList<>();
        private final List<JLabel> statuses = new ArrayList<>();

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return hasVariants ? "Scan the barcode of each size" : "Scan the barcode";
        }

        @Override
        public String help() {
            return "Point the scanner at the barcode, or type the number. No barcode? Skip this — "
                    + "you can still find it by name.";
        }

        @Override
        public String skipLabel() {
            return hasVariants ? "They have no barcodes" : "It has no barcode";
        }

        @Override
        public void skip() {
            for (VDraft v : variants) {
                v.barcode = "";
            }
        }

        @Override
        protected JComponent build() {
            fields.clear();
            statuses.clear();
            JPanel p = new JPanel();
            if (!hasVariants) {
                JTextField f = Wizard.bigField("Scan or type the barcode");
                f.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("barcode", 22, Theme.MUTED));
                f.setText(variants.get(0).barcode);
                JLabel st = new JLabel(" ");
                st.setFont(Theme.font(13));
                fields.add(f);
                statuses.add(st);
                f.addActionListener(e -> wizard.next());
                p = Ui.vbox(8, f, st);
            } else {
                rows(p, variants, v -> v.barcode, v -> {
                    JTextField f = Ui.field("Scan or type");
                    f.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("barcode", 16, Theme.MUTED));
                    return f;
                }, statuses).forEach(fields::add);
            }
            for (int i = 0; i < fields.size(); i++) {
                final int idx = i;
                Ui.onChange(fields.get(i), () -> check(idx));
                check(idx);
            }
            return p;
        }

        private String check(int i) {
            String code = fields.get(i).getText().trim();
            JLabel st = statuses.get(i);
            if (code.isEmpty()) {
                st.setText(" ");
                Ui.setError(fields.get(i), false);
                return null;
            }
            VDraft v = variants.get(i);
            String owner = ctx.inventory().barcodeOwner(code, v.id == null ? -1 : v.id);
            for (int j = 0; j < fields.size() && owner == null; j++) {
                if (j != i && fields.get(j).getText().trim().equals(code)) {
                    owner = label(variants.get(j)) + " (above)";
                }
            }
            if (owner != null) {
                st.setForeground(Theme.BAD);
                st.setText("⚠ Already used by " + owner);
                Ui.setError(fields.get(i), true);
                return "The barcode " + code + " is already used by " + owner + ".";
            }
            st.setForeground(Theme.GOOD);
            st.setText("✓ Barcode is free");
            Ui.setError(fields.get(i), false);
            return null;
        }

        @Override
        public void shown() {
            focusFirstEmpty(fields);
        }

        @Override
        public void commit() {
            for (int i = 0; i < fields.size(); i++) {
                String problem = check(i);
                if (problem != null) {
                    fields.get(i).requestFocusInWindow();
                    throw new AppException(problem);
                }
            }
            for (int i = 0; i < fields.size(); i++) {
                variants.get(i).barcode = fields.get(i).getText().trim();
            }
        }
    }

    private final class PriceStep extends Wizard.Step {
        private final List<JTextField> fields = new ArrayList<>();

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return hasVariants ? "What does each size sell for?" : "What do you sell it for?";
        }

        @Override
        public String help() {
            return fractional() ? "Enter the price for 1 " + unitCode() + ". The till works out the price of any weight."
                    : "The price a customer pays for one.";
        }

        @Override
        protected JComponent build() {
            fields.clear();
            if (!hasVariants) {
                JTextField f = Wizard.bigMoney("0");
                VDraft v = variants.get(0);
                f.setText(v.price >= 0 ? Money.plain(v.price) : "");
                f.addActionListener(e -> wizard.next());
                fields.add(f);
                return withSuffix(f, per());
            }
            JPanel p = new JPanel();
            rows(p, variants, v -> v.price >= 0 ? Money.plain(v.price) : "", v -> Ui.money("0"), null).forEach(fields::add);
            return p;
        }

        @Override
        public void shown() {
            focusFirstEmpty(fields);
        }

        @Override
        public void commit() {
            long[] values = new long[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                String t = fields.get(i).getText().trim();
                String who = hasVariants ? " for " + variants.get(i).name : "";
                if (t.isEmpty()) {
                    fields.get(i).requestFocusInWindow();
                    throw new AppException("Please enter the selling price" + who + ".");
                }
                long v;
                try {
                    v = Money.parse(t);
                } catch (IllegalArgumentException e) {
                    fields.get(i).requestFocusInWindow();
                    throw new AppException(e.getMessage());
                }
                if (v <= 0) {
                    fields.get(i).requestFocusInWindow();
                    throw new AppException("The price" + who + " must be more than zero.");
                }
                values[i] = v;
            }
            for (int i = 0; i < values.length; i++) {
                variants.get(i).price = values[i];
            }
            costWarned = false;
        }

        @Override
        public Wizard.Step then() {
            return variants.stream().anyMatch(v -> v.id == null) && existing != null ? stockStep : null;
        }
    }

    private final class CostStep extends Wizard.Step {
        private final List<JTextField> fields = new ArrayList<>();
        private JLabel hint;

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return hasVariants ? "What did you pay for each size?" : "What did you pay for it?";
        }

        @Override
        public String help() {
            return "Your buying price " + (fractional() ? "for 1 " + unitCode() : "for one")
                    + ". It is used to work out your profit — customers never see it.";
        }

        @Override
        public String skipLabel() {
            return "Add it later";
        }

        @Override
        public void skip() {
            boolean any = false;
            for (VDraft v : variants) {
                any |= v.cost > 0;
            }
            if (!any) {
                for (VDraft v : variants) {
                    v.cost = 0;
                }
            }
        }

        @Override
        protected JComponent build() {
            fields.clear();
            hint = new JLabel(" ");
            hint.setFont(Theme.bold(14));
            if (!hasVariants) {
                JTextField f = Wizard.bigMoney("0");
                VDraft v = variants.get(0);
                f.setText(v.cost > 0 ? Money.plain(v.cost) : "");
                f.addActionListener(e -> wizard.next());
                fields.add(f);
                Ui.onChange(f, this::updateHint);
                updateHint();
                return Ui.vbox(12, withSuffix(f, per()), hint);
            }
            JPanel p = new JPanel();
            rows(p, variants, v -> v.cost > 0 ? Money.plain(v.cost) : "", v -> Ui.money("0"), null).forEach(fields::add);
            return p;
        }

        private void updateHint() {
            if (fields.isEmpty() || hint == null) {
                return;
            }
            long price = variants.get(0).price;
            long cost = Money.parseOrZero(fields.get(0).getText());
            if (cost <= 0 || price <= 0) {
                hint.setText(" ");
                return;
            }
            long profit = price - cost;
            hint.setForeground(profit < 0 ? Theme.BAD : Theme.GOOD);
            hint.setText(profit < 0 ? "You would lose " + Money.format(-profit) + " " + per()
                    : "You make " + Money.format(profit) + " " + per() + " (" + Math.round(profit * 100.0 / price) + "%)");
        }

        @Override
        public void shown() {
            focusFirstEmpty(fields);
        }

        @Override
        public void commit() {
            long[] values = new long[fields.size()];
            String loss = null;
            for (int i = 0; i < fields.size(); i++) {
                String t = fields.get(i).getText().trim();
                try {
                    values[i] = t.isEmpty() ? 0 : Money.parse(t);
                } catch (IllegalArgumentException e) {
                    fields.get(i).requestFocusInWindow();
                    throw new AppException(e.getMessage());
                }
                if (values[i] < 0) {
                    throw new AppException("The buying price cannot be negative.");
                }
                if (values[i] > variants.get(i).price && variants.get(i).price > 0 && loss == null) {
                    loss = label(variants.get(i));
                }
            }
            if (loss != null && !costWarned) {
                costWarned = true;
                throw new AppException("You would sell " + loss + " for less than you paid. If that is right, "
                        + "press Continue again.");
            }
            for (int i = 0; i < values.length; i++) {
                variants.get(i).cost = values[i];
            }
        }
    }

    private final class StockStep extends Wizard.Step {
        private final List<JTextField> fields = new ArrayList<>();
        private final List<VDraft> targets = new ArrayList<>();

        @Override
        public boolean visible() {
            return variants.stream().anyMatch(v -> v.id == null);
        }

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return existing != null ? "How many of the new sizes do you have?" : "How many do you have right now?";
        }

        @Override
        public String help() {
            return "Count what is on the shelf" + (fractional() ? " (decimals are fine, e.g. 12.5)" : "")
                    + ". Later, stock goes down by itself when you sell.";
        }

        @Override
        public String skipLabel() {
            return "None yet";
        }

        @Override
        public void skip() {
            for (VDraft v : variants) {
                if (v.id == null) {
                    v.stock = 0;
                }
            }
        }

        @Override
        protected JComponent build() {
            fields.clear();
            targets.clear();
            for (VDraft v : variants) {
                if (v.id == null) {
                    targets.add(v);
                }
            }
            if (!hasVariants && targets.size() == 1) {
                JTextField f = Wizard.bigField("0");
                f.setHorizontalAlignment(SwingConstants.RIGHT);
                f.setText(targets.get(0).stock > 0 ? Qty.format(targets.get(0).stock) : "");
                f.addActionListener(e -> wizard.next());
                fields.add(f);
                return withSuffix(f, unitCode());
            }
            JPanel p = new JPanel();
            rows(p, targets, v -> v.stock > 0 ? Qty.format(v.stock) : "", v -> {
                JTextField f = Ui.field("0");
                f.setHorizontalAlignment(SwingConstants.RIGHT);
                return f;
            }, null).forEach(fields::add);
            return p;
        }

        @Override
        public void shown() {
            focusFirstEmpty(fields);
        }

        @Override
        public void commit() {
            long[] values = new long[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                String t = fields.get(i).getText().trim();
                try {
                    values[i] = t.isEmpty() ? 0 : Qty.parse(t);
                } catch (IllegalArgumentException e) {
                    fields.get(i).requestFocusInWindow();
                    throw new AppException(e.getMessage());
                }
                if (values[i] < 0) {
                    throw new AppException("Stock cannot be negative.");
                }
                if (!fractional() && !Qty.isWhole(values[i])) {
                    fields.get(i).requestFocusInWindow();
                    throw new AppException("Please use whole numbers — " + unitCode() + " are counted one by one.");
                }
            }
            for (int i = 0; i < values.length; i++) {
                targets.get(i).stock = values[i];
            }
        }
    }

    private final class CategoryStep extends Wizard.Step {
        private JTextField newName;
        private OptionList options;

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return "Which category is it in?";
        }

        @Override
        public String help() {
            return "Categories group your products, so you can find them quickly when selling.";
        }

        @Override
        public String skipLabel() {
            return categoryId == null ? "Skip" : "No category";
        }

        @Override
        public void skip() {
            categoryId = null;
            categoryName = null;
        }

        @Override
        protected JComponent build() {
            newName = Ui.field("New category, e.g. Drinks");
            newName.setFont(Theme.font(15));
            JButton add = Ui.secondary("Add", "plus");
            Runnable create = () -> {
                String n = newName.getText().trim();
                if (n.isEmpty()) {
                    wizard.next();
                    return;
                }
                Category existingCat = ctx.inventory().categories().stream()
                        .filter(c -> c.name().equalsIgnoreCase(n)).findFirst().orElse(null);
                if (existingCat != null) {
                    categoryId = existingCat.id();
                    categoryName = existingCat.name();
                } else {
                    try {
                        categoryId = ctx.inventory().addCategory(n);
                        categoryName = n;
                    } catch (AppException ex) {
                        wizard.showError(ex.getMessage());
                        return;
                    }
                }
                newName.setText("");
                wizard.next();
            };
            add.addActionListener(e -> create.run());
            newName.addActionListener(e -> create.run());
            JPanel newRow = new JPanel(new BorderLayout(8, 0));
            newRow.setOpaque(false);
            newRow.add(newName, BorderLayout.CENTER);
            newRow.add(add, BorderLayout.EAST);
            List<OptionList.Option> list = new ArrayList<>();
            for (Category c : ctx.inventory().categories()) {
                list.add(new OptionList.Option(String.valueOf(c.id()), "tag", c.name(),
                        c.productCount() == 1 ? "1 product" : c.productCount() + " products"));
            }
            options = new OptionList(list);
            if (categoryId != null) {
                options.setSelected(String.valueOf(categoryId));
            }
            options.onPick(k -> {
                categoryId = Long.parseLong(k);
                categoryName = ctx.inventory().categories().stream().filter(c -> c.id() == categoryId)
                        .map(Category::name).findFirst().orElse("");
                wizard.next();
            });
            if (list.isEmpty()) {
                return Ui.vbox(10, newRow, Ui.muted("You have no categories yet. Type one above, or skip."));
            }
            return Ui.vbox(14, newRow, Ui.label("Or choose one"), options);
        }

        @Override
        public void shown() {
            if (options != null && options.selected() != null) {
                options.focusSelectedOrFirst();
            } else {
                newName.requestFocusInWindow();
            }
        }

        @Override
        public void commit() {
            String n = newName.getText().trim();
            if (!n.isEmpty()) {
                Category c = ctx.inventory().categories().stream().filter(x -> x.name().equalsIgnoreCase(n))
                        .findFirst().orElse(null);
                if (c == null) {
                    categoryId = ctx.inventory().addCategory(n);
                    categoryName = n;
                } else {
                    categoryId = c.id();
                    categoryName = c.name();
                }
            } else if (options != null && options.selected() != null) {
                categoryId = Long.parseLong(options.selected());
            }
        }
    }

    // ------------------------------------------------------------ optional details (from the review only)

    private abstract class DetailStep extends Wizard.Step {
        @Override
        public boolean visible() {
            return false;
        }
    }

    private final class LowStockStep extends DetailStep {
        private JTextField field;

        @Override
        public String title() {
            return "When should we warn you that stock is low?";
        }

        @Override
        public String help() {
            return "When stock falls to this number, the product shows “Low stock”. Use 0 to turn the warning off.";
        }

        @Override
        protected JComponent build() {
            field = Wizard.bigField("5");
            field.setHorizontalAlignment(SwingConstants.RIGHT);
            field.addActionListener(e -> wizard.next());
            return withSuffix(field, unitCode());
        }

        @Override
        public void shown() {
            field.setText(Qty.format(minStock));
            field.requestFocusInWindow();
            field.selectAll();
        }

        @Override
        public void commit() {
            long v;
            try {
                v = field.getText().isBlank() ? 0 : Qty.parse(field.getText());
            } catch (IllegalArgumentException e) {
                throw new AppException(e.getMessage());
            }
            if (v < 0) {
                throw new AppException("Use 0 or more.");
            }
            minStock = v;
        }
    }

    private final class SkuStep extends DetailStep {
        private final List<JTextField> fields = new ArrayList<>();

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return "Item code (SKU)";
        }

        @Override
        public String help() {
            return "Optional. Your own short code for the item, if you use one. The till can find products by this code.";
        }

        @Override
        protected JComponent build() {
            fields.clear();
            if (!hasVariants) {
                JTextField f = Wizard.bigField("e.g. CC-150");
                f.setText(variants.get(0).sku);
                f.addActionListener(e -> wizard.next());
                fields.add(f);
                return f;
            }
            JPanel p = new JPanel();
            rows(p, variants, v -> v.sku, v -> Ui.field("optional"), null).forEach(fields::add);
            return p;
        }

        @Override
        public void shown() {
            focusFirstEmpty(fields);
        }

        @Override
        public void commit() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < fields.size(); i++) {
                String s = fields.get(i).getText().trim();
                if (!s.isEmpty() && !seen.add(s.toLowerCase(Locale.ROOT))) {
                    throw new AppException("The code " + s + " is used twice.");
                }
            }
            for (int i = 0; i < fields.size(); i++) {
                variants.get(i).sku = fields.get(i).getText().trim();
            }
        }
    }

    private final class DescriptionStep extends DetailStep {
        private JTextArea area;

        @Override
        public String title() {
            return "Description";
        }

        @Override
        public String help() {
            return "Optional notes about the product, for your own use.";
        }

        @Override
        protected JComponent build() {
            area = new JTextArea(5, 30);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(Theme.font(15));
            area.setText(Text.trim(description));
            JScrollPane sp = new JScrollPane(area);
            sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER_STRONG));
            return sp;
        }

        @Override
        public void shown() {
            area.requestFocusInWindow();
        }

        @Override
        public void commit() {
            description = Text.trimToNull(area.getText());
        }
    }

    private final class SupplierStep extends DetailStep {
        private OptionList options;

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return "Who do you usually buy it from?";
        }

        @Override
        public String help() {
            return "Optional. Helps when you reorder.";
        }

        @Override
        public String skipLabel() {
            return "No supplier";
        }

        @Override
        public void skip() {
            supplierId = null;
            supplierName = null;
        }

        @Override
        protected JComponent build() {
            List<OptionList.Option> list = new ArrayList<>();
            for (Supplier s : ctx.parties().suppliers(false)) {
                list.add(new OptionList.Option(String.valueOf(s.id()), "suppliers", s.name(), Text.trim(s.phone())));
            }
            if (list.isEmpty()) {
                return Ui.muted("You have no suppliers yet. Add them under Suppliers.");
            }
            options = new OptionList(list);
            if (supplierId != null) {
                options.setSelected(String.valueOf(supplierId));
            }
            options.onPick(k -> {
                supplierId = Long.parseLong(k);
                supplierName = list.stream().filter(o -> o.key().equals(k)).map(OptionList.Option::title)
                        .findFirst().orElse("");
                wizard.next();
            });
            return options;
        }

        @Override
        public void shown() {
            if (options != null) {
                options.focusSelectedOrFirst();
            }
        }
    }

    private final class PictureStep extends DetailStep {
        private JLabel preview;

        @Override
        public String title() {
            return "Add a picture";
        }

        @Override
        public String help() {
            return "Optional. It is shown next to the product on the Sales screen.";
        }

        @Override
        protected JComponent build() {
            preview = new JLabel();
            preview.setPreferredSize(new java.awt.Dimension(140, 140));
            preview.setHorizontalAlignment(SwingConstants.CENTER);
            preview.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            JButton choose = Ui.primary("Choose a picture…", "image");
            choose.addActionListener(e -> choose());
            JButton remove = Ui.ghost("Remove picture", "trash");
            remove.addActionListener(e -> {
                image = null;
                imageChanged = true;
                showPreview();
            });
            showPreview();
            JPanel p = new JPanel(new BorderLayout(16, 0));
            p.setOpaque(false);
            p.add(preview, BorderLayout.WEST);
            p.add(Ui.vbox(8, choose, remove), BorderLayout.CENTER);
            return p;
        }

        private void choose() {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Pictures (JPG, PNG, GIF, BMP)", "jpg", "jpeg", "png", "gif", "bmp"));
            if (fc.showOpenDialog(wizard) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                BufferedImage src = ImageIO.read(fc.getSelectedFile());
                if (src == null) {
                    throw new IOException("This file is not a picture we can open.");
                }
                image = thumbnail(src, 320);
                imageChanged = true;
                showPreview();
            } catch (IOException e) {
                wizard.showError("The picture could not be opened: " + e.getMessage());
            }
        }

        private void showPreview() {
            if (image == null) {
                preview.setIcon(Icons.get("image", 40, Theme.BORDER_STRONG));
                return;
            }
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(image));
                double s = Math.min(136.0 / img.getWidth(), 136.0 / img.getHeight());
                preview.setIcon(new ImageIcon(img.getScaledInstance((int) (img.getWidth() * s),
                        (int) (img.getHeight() * s), java.awt.Image.SCALE_SMOOTH)));
            } catch (IOException | RuntimeException e) {
                preview.setIcon(Icons.get("warning", 30, Theme.BAD));
            }
        }
    }

    private final class PackStep extends DetailStep {
        private JTextField value;
        private JComboBox<String> packUnitBox;

        @Override
        public String title() {
            return "How big is one pack?";
        }

        @Override
        public String help() {
            return "Optional, for your information — e.g. a 500 g bag or a 1.5 L bottle.";
        }

        @Override
        protected JComponent build() {
            value = Wizard.bigField("e.g. 500");
            value.setText(Text.trim(packValue));
            value.addActionListener(e -> wizard.next());
            packUnitBox = new JComboBox<>(new String[] {"g", "kg", "ml", "L"});
            packUnitBox.setFont(Theme.font(16));
            if (packUnit != null) {
                packUnitBox.setSelectedItem(packUnit);
            }
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setOpaque(false);
            p.add(value, BorderLayout.CENTER);
            p.add(packUnitBox, BorderLayout.EAST);
            return p;
        }

        @Override
        public void shown() {
            value.requestFocusInWindow();
        }

        @Override
        public void commit() {
            String v = value.getText().trim();
            if (!v.isEmpty()) {
                try {
                    if (new java.math.BigDecimal(v).signum() < 0) {
                        throw new AppException("The size cannot be negative.");
                    }
                } catch (NumberFormatException e) {
                    throw new AppException("Please type a number, e.g. 500 or 1.5.");
                }
            }
            packValue = v.isEmpty() ? null : v;
            packUnit = v.isEmpty() ? null : (String) packUnitBox.getSelectedItem();
        }
    }

    // ------------------------------------------------------------ review

    private final class ReviewStep extends Wizard.Step {

        @Override
        public boolean dynamic() {
            return true;
        }

        @Override
        public String title() {
            return existing == null ? "Check and save" : name;
        }

        @Override
        public String help() {
            return existing == null ? "Make sure everything looks right. Tap “Change” to fix anything."
                    : "Tap “Change” next to anything you want to edit, then save.";
        }

        @Override
        public String nextLabel() {
            return existing == null ? "Save product" : "Save changes";
        }

        @Override
        public String skipLabel() {
            return existing == null ? "Save and add another" : null;
        }

        @Override
        public void skip() {
            save();
            addAnother = true;
        }

        @Override
        protected JComponent build() {
            boolean unitChangeable = existing == null || ctx.inventory().canChangeUnit(existing.id());
            List<JComponent> rows = new ArrayList<>();
            rows.add(Wizard.summaryRow("Name", name, () -> wizard.jumpTo(nameStep)));
            rows.add(Wizard.summaryRow("Sold", soldByText() + (unitChangeable ? "" : "  (can't change after sales)"),
                    unitChangeable ? () -> wizard.jumpTo(soldStep) : null));
            rows.add(Wizard.summaryRow("Sizes or types", hasVariants
                    ? variants.stream().map(v -> v.name).collect(Collectors.joining(", ")) : "No — just one",
                    () -> wizard.jumpTo(sizesStep)));
            rows.add(Wizard.summaryRow(hasVariants ? "Barcodes" : "Barcode", perVariant(v -> v.barcode.isBlank()
                    ? "none" : v.barcode, "No barcode"), () -> wizard.jumpTo(barcodeStep)));
            rows.add(Wizard.summaryRow("Selling price", perVariant(v -> v.price < 0 ? "not set"
                    : Money.format(v.price) + (hasVariants ? "" : " " + per()), null), () -> wizard.jumpTo(priceStep)));
            rows.add(Wizard.summaryRow("Buying price", variants.stream().allMatch(v -> v.cost == 0) ? "Not added"
                    : perVariant(v -> v.cost == 0 ? "not added" : Money.format(v.cost), null),
                    () -> wizard.jumpTo(costStep)));
            if (existing == null) {
                rows.add(Wizard.summaryRow("Stock now", perVariant(v -> Qty.format(v.stock, unitCode()), null),
                        () -> wizard.jumpTo(stockStep)));
            } else {
                String now = perVariant(v -> v.id == null ? Qty.format(v.stock, unitCode()) + " (new)"
                        : Qty.format(v.current, unitCode()), null);
                boolean newSizes = variants.stream().anyMatch(v -> v.id == null);
                JPanel stockRow = Wizard.summaryRow("Stock now", now, newSizes ? () -> wizard.jumpTo(stockStep)
                        : this::changeStock);
                rows.add(stockRow);
                if (!newSizes) {
                    JLabel note = Ui.muted("<html><div style='width:430px'>Stock changes are saved straight away and "
                            + "kept in the stock history.</div></html>");
                    note.setFont(Theme.font(12));
                    note.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 0));
                    rows.add(note);
                }
            }
            rows.add(Wizard.summaryRow("Category", categoryId == null ? "None" : categoryName,
                    () -> wizard.jumpTo(categoryStep)));
            JPanel details = Ui.vbox(0,
                    Wizard.summaryRow("Low-stock warning", minStock == 0 ? "Off" : "At " + Qty.format(minStock, unitCode()),
                            () -> wizard.jumpTo(lowStockStep)),
                    Wizard.summaryRow("Item code (SKU)", perVariant(v -> v.sku.isBlank() ? "none" : v.sku, "None"),
                            () -> wizard.jumpTo(skuStep)),
                    Wizard.summaryRow("Description", Text.blank(description) ? "None" : description,
                            () -> wizard.jumpTo(descriptionStep)),
                    Wizard.summaryRow("Supplier", supplierId == null ? "None" : supplierName,
                            () -> wizard.jumpTo(supplierStep)),
                    Wizard.summaryRow("Picture", image == null ? "None" : "Added", () -> wizard.jumpTo(pictureStep)),
                    Wizard.summaryRow("Pack size", Text.blank(packValue) ? "None" : packValue + " " + packUnit,
                            () -> wizard.jumpTo(packStep)));
            Collapsible more = new Collapsible("Show more details (optional)", details);
            rows.add(more);
            JPanel p = Ui.vbox(0, rows.toArray(new JComponent[0]));
            return p;
        }

        /** Opens "Change stock" for this product and shows the new stock here afterwards. */
        private void changeStock() {
            if (StockFlow.open(wizard, ctx, existing.id(), null)) {
                Product fresh = ctx.inventory().product(existing.id());
                if (fresh != null) {
                    for (VDraft d : variants) {
                        for (Variant v : fresh.activeVariants()) {
                            if (d.id != null && v.id() == d.id) {
                                d.current = v.stockQty();
                            }
                        }
                    }
                }
                wizard.refreshCurrent();
            }
        }

        /** "1 value" for a simple product, "Small: x, Medium: y" for sizes. */
        private String perVariant(Function<VDraft, String> f, String allEmpty) {
            if (!hasVariants) {
                String v = f.apply(variants.get(0));
                return allEmpty != null && ("none".equals(v) || v.isBlank()) ? allEmpty : v;
            }
            List<String> parts = new ArrayList<>();
            boolean allBlank = true;
            for (VDraft v : variants) {
                String s = f.apply(v);
                allBlank &= "none".equals(s);
                parts.add(v.name + ": " + s);
            }
            return allBlank && allEmpty != null ? allEmpty : String.join("\n", parts);
        }

        @Override
        public void commit() {
            save();
        }
    }

    private void save() {
        if (name.isBlank()) {
            throw new AppException("Please give the product a name.");
        }
        if (unit == null) {
            throw new AppException("Please choose how you sell it.");
        }
        for (VDraft v : variants) {
            if (v.price < 0) {
                throw new AppException("Please set the selling price" + (hasVariants ? " for " + v.name : "") + ".");
            }
        }
        List<InventoryService.VariantForm> forms = new ArrayList<>();
        for (VDraft v : variants) {
            forms.add(new InventoryService.VariantForm(v.id, hasVariants ? v.name : "", v.barcode, v.sku, v.cost,
                    v.price, v.id == null ? v.stock : 0, minStock, true));
        }
        InventoryService.ProductForm form = new InventoryService.ProductForm(existing == null ? null : existing.id(),
                name, categoryId, description, unit.id(), packValue, packValue == null ? null : packUnit, supplierId,
                hasVariants, forms);
        savedId = ctx.inventory().saveProduct(form);
        if (imageChanged) {
            ctx.inventory().setImage(savedId, image);
        }
    }

    /** Scale a picture to fit {@code max} px and encode it as JPEG (stored in the database, so backups include it). */
    public static byte[] thumbnail(BufferedImage src, int max) throws IOException {
        double scale = Math.min(1.0, max / (double) Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) (src.getWidth() * scale));
        int h = Math.max(1, (int) (src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        return bos.toByteArray();
    }
}
