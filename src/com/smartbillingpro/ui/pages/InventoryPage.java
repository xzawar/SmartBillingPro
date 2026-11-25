package com.smartbillingpro.ui.pages;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartbillingpro.model.Category;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.StockMovement;
import com.smartbillingpro.model.StockStatus;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.service.DashboardService;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.EmptyState;
import com.smartbillingpro.ui.Icons;
import com.smartbillingpro.ui.LineChart;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PillGroup;
import com.smartbillingpro.ui.RowList;
import com.smartbillingpro.ui.ScrollablePanel;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.flows.ProductFlow;
import com.smartbillingpro.ui.flows.StockFlow;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Products on the left, everything about the chosen product on the right:
 * stock, prices, sales over the last 30 days and recent stock changes.
 */
public final class InventoryPage extends Page {

    private enum Show {
        ALL("All"), LOW("Low stock"), OUT("Out of stock"), HIDDEN("Hidden");

        final String label;

        Show(String label) {
            this.label = label;
        }
    }

    private final JTabbedPane tabs = new JTabbedPane();
    // products
    private final CardLayout productCards = new CardLayout();
    private final JPanel productHolder = new JPanel(productCards);
    private final JTextField search = Ui.search("Search by name, barcode or code");
    private final PillGroup<Show> show = new PillGroup<>();
    private final JComboBox<Object> categoryFilter = new JComboBox<>();
    private final RowList<Product> products = new RowList<>(this::row, 60);
    private final Detail detail = new Detail();
    private List<Product> all = List.of();
    private boolean loading;
    // categories
    private final RowList<Category> categories = new RowList<>(c -> RowList.Row.of(c.name(),
            c.productCount() == 1 ? "1 product" : c.productCount() + " products", null).withIcon("tag"), 56);
    private final JButton renameCategory = Ui.secondary("Rename", "edit");
    private final JButton deleteCategory = Ui.ghost("Delete", "trash");
    // stock history
    private final DateRangeBar movementRange = new DateRangeBar(DateRangeBar.Preset.LAST_30);
    private final JTextField movementSearch = Ui.search("Search product or note");
    private final DataTable<StockMovement> movements = new DataTable<>();

    public InventoryPage(MainFrame frame) {
        super(frame, "Inventory", "Your products and how much you have of each");
        JButton add = Ui.primary("Add product", "plus");
        add.setToolTipText("Add a new product (Ctrl+N)");
        add.addActionListener(e -> addProduct());
        addAction(add);
        tabs.addTab("Products", buildProducts());
        tabs.addTab("Stock history", buildMovements());
        tabs.addTab("Categories", buildCategories());
        tabs.addChangeListener(e -> refresh());
        setBody(tabs);
        Ui.key(this, "ctrl N", () -> {
            if (isShowing()) {
                addProduct();
            }
        });
    }

    // ================================================================ products

    private JComponent buildProducts() {
        show.setItems(List.of(Show.values()), s -> s.label);
        show.onChange(s -> applyFilters());
        categoryFilter.addActionListener(e -> applyFilters());
        categoryFilter.setToolTipText("Show one category");
        Ui.onChange(search, () -> {
            products.filter(search.getText());
            if (products.selected() == null) {
                products.selectFirst();
            }
        });
        search.setColumns(18);
        products.onSelect(p -> detail.show(p));
        products.onOpen(p -> editProduct(p), false);
        products.setEmptyState(new EmptyState("inventory", "Nothing to show", "No products match this filter.", true));
        show.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        search.setColumns(20);
        JPanel bar = Ui.toolbar(new java.awt.Component[] {search, show, categoryFilter});

        Card listCard = new Card(new BorderLayout());
        listCard.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        listCard.add(products, BorderLayout.CENTER);
        JPanel split = new JPanel(new GridBagLayout());
        split.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 0.44;
        listCard.setPreferredSize(new Dimension(100, 100));
        split.add(listCard, c);
        c.gridx = 1;
        c.weightx = 0.56;
        c.insets = new Insets(0, 14, 0, 0);
        detail.setPreferredSize(new Dimension(100, 100));
        split.add(detail, c);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        content.add(bar, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        EmptyState none = new EmptyState("inventory", "Add your first product",
                "Add the things you sell – one question at a time, it only takes a few seconds. "
                        + "You can also scan a new barcode on the Sales screen.")
                .action("Add a product", "plus", this::addProduct);
        productHolder.setOpaque(false);
        productHolder.add(content, "content");
        productHolder.add(none, "empty");
        return productHolder;
    }

    private RowList.Row row(Product p) {
        List<Variant> vs = p.activeVariants();
        StockStatus st = stockStatus(p);
        String stockText;
        if (!p.active()) {
            stockText = "Hidden";
        } else if (p.hasVariants() && st != StockStatus.IN_STOCK) {
            stockText = st == StockStatus.OUT ? "Some sizes out" : "Some sizes low";
        } else {
            stockText = switch (st) {
                case OUT -> "Out of stock";
                case LOW -> "Only " + Qty.format(Math.max(0, p.totalStock()), p.unitCode()) + " left";
                default -> Qty.format(p.totalStock(), p.unitCode()) + " in stock";
            };
        }
        Ui.Tone tone = !p.active() ? Ui.Tone.NEUTRAL : st == StockStatus.OUT ? Ui.Tone.BAD
                : st == StockStatus.LOW ? Ui.Tone.WARN : Ui.Tone.NEUTRAL;
        String sub = (vs.size() > 1 ? vs.size() + " sizes" : Text.blank(barcode(p)) ? "No barcode" : barcode(p))
                + (Text.blank(p.categoryName()) ? "" : "  •  " + p.categoryName());
        return RowList.Row.of(p.name(), sub, priceText(p)).withStatus(stockText, tone).dimmed(!p.active());
    }

    private static String barcode(Product p) {
        Variant v = first(p);
        return v == null ? null : v.barcode();
    }

    private static Variant first(Product p) {
        List<Variant> a = p.activeVariants();
        return a.isEmpty() ? p.firstVariant() : a.get(0);
    }

    static String priceText(Product p) {
        List<Variant> vs = p.activeVariants();
        if (vs.isEmpty()) {
            return "—";
        }
        long lo = vs.stream().mapToLong(Variant::salePrice).min().orElse(0);
        long hi = vs.stream().mapToLong(Variant::salePrice).max().orElse(0);
        String s = lo == hi ? Money.format(lo) : Money.format(lo) + " – " + Money.grouped(hi);
        return p.fractional() ? s + " / " + p.unitCode() : s;
    }

    private static StockStatus stockStatus(Product p) {
        StockStatus worst = StockStatus.IN_STOCK;
        for (Variant v : p.activeVariants()) {
            StockStatus s = StockStatus.of(v.stockQty(), v.minStock());
            if (s.ordinal() > worst.ordinal()) {
                worst = s;
            }
        }
        return p.activeVariants().isEmpty() ? StockStatus.OUT : worst;
    }

    private void applyFilters() {
        if (loading) {
            return;
        }
        Object cat = categoryFilter.getSelectedItem();
        Show s = show.selected() == null ? Show.ALL : show.selected();
        List<Product> rows = all.stream()
                .filter(p -> !(cat instanceof Category c) || Objects.equals(c.id(), p.categoryId()))
                .filter(p -> switch (s) {
                    case LOW -> p.active() && stockStatus(p) == StockStatus.LOW;
                    case OUT -> p.active() && stockStatus(p) == StockStatus.OUT;
                    case HIDDEN -> !p.active();
                    default -> p.active();
                })
                .toList();
        products.setEmptyState(new EmptyState(s == Show.LOW || s == Show.OUT ? "check" : "inventory",
                switch (s) {
                    case LOW -> "Nothing is running low";
                    case OUT -> "Nothing is out of stock";
                    case HIDDEN -> "No hidden products";
                    default -> "No products here";
                },
                switch (s) {
                    case LOW, OUT -> "Good news – every product has enough stock.";
                    case HIDDEN -> "Products you hide from the Sales screen are kept here.";
                    default -> "Nothing in this category yet.";
                }, true));
        products.setItems(rows);
        products.filter(search.getText());
        if (products.selected() == null) {
            products.selectFirst();
        }
        detail.show(products.selected());
    }

    private void updateCounts() {
        long low = all.stream().filter(p -> p.active() && stockStatus(p) == StockStatus.LOW).count();
        long out = all.stream().filter(p -> p.active() && stockStatus(p) == StockStatus.OUT).count();
        long hidden = all.stream().filter(p -> !p.active()).count();
        Show keep = show.selected();
        show.setItems(List.of(Show.values()), s -> switch (s) {
            case LOW -> low > 0 ? "Low stock • " + low : "Low stock";
            case OUT -> out > 0 ? "Out of stock • " + out : "Out of stock";
            case HIDDEN -> hidden > 0 ? "Hidden • " + hidden : "Hidden";
            default -> "All";
        });
        show.select(keep == null ? Show.ALL : keep);
        long active = all.stream().filter(Product::active).count();
        long value = all.stream().filter(Product::active).flatMap(p -> p.activeVariants().stream())
                .mapToLong(v -> Money.mulQty(v.costPrice(), Math.max(0, v.stockQty()))).sum();
        setSubtitle(active == 0 ? "Your products and how much you have of each"
                : active + (active == 1 ? " product" : " products") + "  •  stock worth " + Money.format(value)
                + " at buying price");
    }

    private void addProduct() {
        ProductFlow.Result r = ProductFlow.add(this, ctx, null, null);
        while (r.addAnother()) {
            Toast.ok(this, "Product added.");
            r = ProductFlow.add(this, ctx, null, null);
        }
        if (r.productId() != null) {
            Toast.ok(this, "Product added.");
        }
        tabs.setSelectedIndex(0);
        refresh();
        if (r.productId() != null) {
            long id = r.productId();
            show.select(Show.ALL);
            applyFilters();
            products.items().stream().filter(p -> p.id() == id).findFirst().ifPresent(products::select);
        }
    }

    private void editProduct(Product p) {
        if (p == null) {
            return;
        }
        if (ProductFlow.edit(this, ctx, p.id())) {
            Toast.ok(this, "Changes saved.");
            reselect(p.id());
        }
    }

    private void changeStock(Product p) {
        if (p == null) {
            return;
        }
        if (StockFlow.open(this, ctx, p.id(), null)) {
            Toast.ok(this, "Stock updated. The change is saved in the stock history.");
            reselect(p.id());
        }
    }

    private void reselect(long id) {
        refresh();
        products.items().stream().filter(x -> x.id() == id).findFirst().ifPresent(products::select);
        detail.show(products.selected());
    }

    private void toggleHidden(Product p) {
        if (p.active() && !Msg.confirm(this, "Hide " + p.name() + "?", "It will no longer show on the Sales screen. "
                + "Its sales stay in your reports, and you can show it again any time.", "Hide")) {
            return;
        }
        safely(() -> {
            ctx.inventory().setArchived(p.id(), p.active());
            Toast.ok(this, p.active() ? p.name() + " is hidden." : p.name() + " is back on the Sales screen.");
            refresh();
        });
    }

    private void deleteProduct(Product p) {
        if (!ctx.inventory().canDelete(p.id())) {
            Msg.info(this, "This product can't be deleted", p.name() + " has stock or sales history, so deleting it "
                    + "would make your records wrong. Hide it instead – it disappears from the Sales screen but "
                    + "reports stay correct.");
            return;
        }
        if (Msg.confirm(this, "Delete " + p.name() + "?", "It has never been sold or stocked, so nothing else changes.",
                "Delete")) {
            safely(() -> {
                ctx.inventory().delete(p.id());
                Toast.ok(this, "Product deleted.");
                refresh();
            });
        }
    }

    /** Everything about one product. */
    private final class Detail extends JPanel {
        private final CardLayout cards = new CardLayout();
        private final ScrollablePanel body = new ScrollablePanel(null);
        private Product product;

        Detail() {
            setLayout(cards);
            setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setOpaque(false);
            JScrollPane sp = new JScrollPane(body);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.setOpaque(false);
            sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(24);
            sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            Card card = new Card(new BorderLayout());
            card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
            card.add(sp, BorderLayout.CENTER);
            add(card, "product");
            Card empty = new Card(new BorderLayout());
            empty.add(new EmptyState("inventory", "Choose a product", "Its stock, prices and sales show up here.", true),
                    BorderLayout.CENTER);
            add(empty, "none");
            cards.show(this, "none");
        }

        void show(Product p) {
            product = p;
            if (p == null) {
                cards.show(this, "none");
                return;
            }
            body.removeAll();
            add(header(p));
            add(tiles(p));
            if (p.activeVariants().size() > 1) {
                add(section("Sizes"));
                for (Variant v : p.activeVariants()) {
                    add(sizeRow(p, v));
                }
            }
            add(salesChart(p));
            add(recentChanges(p));
            body.add(javax.swing.Box.createVerticalGlue());
            body.revalidate();
            body.repaint();
            cards.show(this, "product");
            javax.swing.SwingUtilities.invokeLater(() -> body.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, 1)));
        }

        private void add(JComponent c) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(c);
        }

        private JComponent header(Product p) {
            JLabel name = new JLabel(p.name());
            name.setFont(Theme.bold(20));
            name.setForeground(Theme.TEXT);
            String kind = p.fractional() ? "Sold by " + p.unitCode() : "Sold by the piece";
            JLabel sub = Ui.muted((Text.blank(p.categoryName()) ? "No category" : p.categoryName()) + "  •  " + kind
                    + (Text.blank(p.supplierName()) ? "" : "  •  from " + p.supplierName()));
            sub.setFont(Theme.font(13));
            JPanel titles = new JPanel(new BorderLayout(0, 4));
            titles.setOpaque(false);
            titles.add(name, BorderLayout.NORTH);
            titles.add(sub, BorderLayout.CENTER);

            JButton stock = Ui.primary("Change stock", "inventory");
            stock.setToolTipText("Add or remove stock, with a reason");
            stock.addActionListener(e -> changeStock(product));
            stock.setEnabled(p.active());
            JButton edit = Ui.secondary("Edit", "edit");
            edit.setToolTipText("Change the name, prices, barcode or anything else");
            edit.addActionListener(e -> editProduct(product));
            JButton more = Ui.icon("chevron-down", "More");
            more.setText("More");
            more.setHorizontalTextPosition(SwingConstants.LEFT);
            more.addActionListener(e -> {
                JPopupMenu menu = new JPopupMenu();
                JMenuItem hide = new JMenuItem(product.active() ? "Hide from the Sales screen" : "Show on the Sales screen again");
                hide.addActionListener(x -> toggleHidden(product));
                JMenuItem history = new JMenuItem("See all stock history");
                history.addActionListener(x -> openHistory(product));
                JMenuItem del = new JMenuItem("Delete product…");
                del.addActionListener(x -> deleteProduct(product));
                menu.add(hide);
                menu.add(history);
                menu.addSeparator();
                menu.add(del);
                menu.show(more, 0, more.getHeight() + 4);
            });
            JPanel actions = Ui.flowLeft(8, stock, edit, more);
            JPanel h = new JPanel(new BorderLayout(0, 14));
            h.setOpaque(false);
            h.add(titles, BorderLayout.NORTH);
            h.add(actions, BorderLayout.CENTER);
            if (!p.active()) {
                JLabel hidden = Ui.pill("Hidden from the Sales screen", Ui.Tone.NEUTRAL);
                h.add(Ui.flowLeft(0, hidden), BorderLayout.SOUTH);
            }
            h.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
            return capped(h);
        }

        private JComponent tiles(Product p) {
            List<Variant> vs = p.activeVariants();
            StockStatus st = stockStatus(p);
            JPanel grid = new JPanel(new GridLayout(1, 3, 10, 0));
            grid.setOpaque(false);
            grid.add(tile("In stock", Qty.format(p.totalStock(), p.unitCode()),
                    st == StockStatus.OUT ? "Out of stock" : st == StockStatus.LOW ? "Running low" : "Enough for now",
                    st == StockStatus.OUT ? Ui.Tone.BAD : st == StockStatus.LOW ? Ui.Tone.WARN : Ui.Tone.GOOD));
            grid.add(tile("Sells for", priceText(p), vs.size() > 1 ? "depends on the size" : p.fractional()
                    ? "per " + p.unitCode() : "each", Ui.Tone.NEUTRAL));
            Variant v = first(p);
            if (v == null || v.costPrice() <= 0 || vs.size() > 1) {
                long withCost = vs.stream().filter(x -> x.costPrice() > 0).count();
                grid.add(tile("Profit", vs.size() > 1 && withCost > 0 ? "See sizes" : "—",
                        withCost == 0 ? "Add the buying price to see it" : "differs by size", Ui.Tone.NEUTRAL));
            } else {
                long profit = v.salePrice() - v.costPrice();
                grid.add(tile("Profit", Money.format(profit), v.salePrice() > 0
                        ? Math.round(profit * 100.0 / v.salePrice()) + "% of the price" : "", profit < 0 ? Ui.Tone.BAD
                        : Ui.Tone.GOOD));
            }
            grid.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
            return capped(grid);
        }

        private JComponent tile(String label, String value, String hint, Ui.Tone tone) {
            Card c = new Card(new BorderLayout(0, 4));
            c.setOutlined(false);
            c.setBackground(Theme.SURFACE_ALT);
            c.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            JLabel l = Ui.muted(label);
            l.setFont(Theme.font(12));
            JLabel v = new JLabel(value);
            v.setFont(Theme.bold(18));
            v.setForeground(Theme.TEXT);
            JLabel h = new JLabel(hint);
            h.setFont(Theme.font(12));
            h.setForeground(tone == Ui.Tone.NEUTRAL ? Theme.MUTED : tone.fg);
            c.add(l, BorderLayout.NORTH);
            c.add(v, BorderLayout.CENTER);
            c.add(h, BorderLayout.SOUTH);
            return c;
        }

        private JComponent section(String text) {
            JLabel l = new JLabel(text);
            l.setFont(Theme.bold(15));
            l.setForeground(Theme.TEXT);
            l.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
            return capped(l);
        }

        private JComponent sizeRow(Product p, Variant v) {
            StockStatus st = StockStatus.of(v.stockQty(), v.minStock());
            JPanel r = new JPanel(new BorderLayout(10, 0));
            r.setOpaque(false);
            r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(8, 0, 8, 0)));
            JLabel name = new JLabel(v.name());
            name.setFont(Theme.bold(14));
            JLabel code = Ui.muted(Text.blank(v.barcode()) ? "no barcode" : v.barcode());
            code.setFont(Theme.font(12));
            JPanel left = new JPanel(new BorderLayout(0, 2));
            left.setOpaque(false);
            left.add(name, BorderLayout.NORTH);
            left.add(code, BorderLayout.CENTER);
            JLabel price = new JLabel(Money.format(v.salePrice()));
            price.setFont(Theme.bold(14));
            JLabel stock = Ui.pill(Qty.format(v.stockQty(), p.unitCode()), st == StockStatus.OUT ? Ui.Tone.BAD
                    : st == StockStatus.LOW ? Ui.Tone.WARN : Ui.Tone.NEUTRAL);
            r.add(left, BorderLayout.CENTER);
            r.add(Ui.flowRight(12, price, stock), BorderLayout.EAST);
            return capped(r);
        }

        private JComponent salesChart(Product p) {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(29);
            List<DashboardService.Point> pts = ctx.dashboard().productSales(p.id(), from, to);
            long qty = pts.stream().mapToLong(DashboardService.Point::value).sum();
            long money = pts.stream().mapToLong(DashboardService.Point::value2).sum();
            LineChart chart = new LineChart("Not sold in the last 30 days");
            String unit = p.unitCode();
            chart.setFormat(v -> Qty.format(v, unit), v -> Qty.format(v));
            chart.setScale(Qty.ONE, !p.fractional());
            chart.setBars(true);
            chart.setData(pts.stream().map(x -> new LineChart.Point(x.label(), x.value(), 0)).toList(), "Sold", null);
            chart.setPreferredSize(new Dimension(300, 190));
            JLabel title = new JLabel("Sold in the last 30 days");
            title.setFont(Theme.bold(15));
            title.setForeground(Theme.TEXT);
            JLabel sum = Ui.muted(qty == 0 ? " " : Qty.format(qty, unit) + "  •  " + Money.format(money));
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(title, BorderLayout.WEST);
            head.add(sum, BorderLayout.EAST);
            JPanel box = new JPanel(new BorderLayout(0, 6));
            box.setOpaque(false);
            box.setBorder(BorderFactory.createEmptyBorder(14, 0, 14, 0));
            box.add(head, BorderLayout.NORTH);
            box.add(chart, BorderLayout.CENTER);
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
            return box;
        }

        private JComponent recentChanges(Product p) {
            List<StockMovement> list = ctx.inventory().recentMovements(p.id(), 6);
            JPanel box = new JPanel();
            box.setOpaque(false);
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            JLabel title = new JLabel("Recent stock changes");
            title.setFont(Theme.bold(15));
            title.setForeground(Theme.TEXT);
            JButton all = Ui.ghost("See all", null);
            all.addActionListener(e -> openHistory(product));
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(title, BorderLayout.WEST);
            if (list.size() > 5) {
                head.add(all, BorderLayout.EAST);
            }
            head.setAlignmentX(Component.LEFT_ALIGNMENT);
            head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
            box.add(head);
            if (list.isEmpty()) {
                JLabel none = Ui.muted("No stock changes yet.");
                none.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
                box.add(none);
            }
            for (StockMovement m : list.stream().limit(5).toList()) {
                JPanel r = new JPanel(new BorderLayout(10, 0));
                r.setOpaque(false);
                r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                        BorderFactory.createEmptyBorder(8, 0, 8, 0)));
                String what = movementText(m);
                JLabel l1 = new JLabel(what + (p.hasVariants() && !Text.blank(m.variantName()) ? " – " + m.variantName() : ""));
                l1.setFont(Theme.bold(13));
                JLabel l2 = Ui.muted(Dates.formatDateTime(m.createdAt()) + (Text.blank(m.reference()) ? "" : "  •  " + m.reference()));
                l2.setFont(Theme.font(12));
                JPanel left = new JPanel(new BorderLayout(0, 2));
                left.setOpaque(false);
                left.add(l1, BorderLayout.NORTH);
                left.add(l2, BorderLayout.CENTER);
                JLabel change = new JLabel((m.qtyChange() > 0 ? "+" : "−") + Qty.format(Math.abs(m.qtyChange())));
                change.setFont(Theme.bold(14));
                change.setForeground(m.qtyChange() > 0 ? Theme.GOOD : Theme.TEXT);
                JLabel after = Ui.muted("now " + Qty.format(m.balanceAfter(), m.unitCode()));
                r.add(left, BorderLayout.CENTER);
                r.add(Ui.flowRight(10, change, after), BorderLayout.EAST);
                r.setAlignmentX(Component.LEFT_ALIGNMENT);
                r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
                box.add(r);
            }
            box.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            return box;
        }

        private JComponent capped(JComponent c) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            return c;
        }
    }

    /** Plain words for a stock change, using the reason for changes made by hand. */
    static String movementText(StockMovement m) {
        if (m.type().startsWith("ADJUSTMENT") && !Text.blank(m.reason())) {
            String r = m.reason();
            int dash = r.indexOf(" - ");
            return dash > 0 ? r.substring(0, dash) : r;
        }
        return m.typeLabel();
    }

    private void openHistory(Product p) {
        tabs.setSelectedIndex(1);
        movementSearch.setText(p.name());
    }

    // ================================================================ stock history

    private JComponent buildMovements() {
        movements.col("When", DataTable.Kind.DATETIME, StockMovement::createdAt, 140)
                .text("Product", StockMovement::displayName, 220)
                .text("What happened", InventoryPage::movementText, 170)
                .col("Change", DataTable.Kind.TEXT, m -> (m.qtyChange() > 0 ? "+" : "−") + Qty.format(Math.abs(m.qtyChange())), 80)
                .col("Stock after", DataTable.Kind.TEXT, m -> Qty.format(m.balanceAfter(), m.unitCode()), 100)
                .text("Bill / note", m -> Text.orDash(Text.blank(m.reference()) ? noteOf(m) : m.reference()), 200);
        javax.swing.table.DefaultTableCellRenderer right = new javax.swing.table.DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        right.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        movements.table().getColumnModel().getColumn(3).setCellRenderer(right);
        movements.table().getColumnModel().getColumn(4).setCellRenderer(right);
        movements.setEmptyText("No stock changes in this period. Sales, purchases and changes made by hand appear here.");
        movementRange.onChange(this::loadMovements);
        Ui.onChange(movementSearch, () -> movements.filter(movementSearch.getText()));
        movementSearch.setColumns(20);
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.add(movementRange, BorderLayout.CENTER);
        bar.add(movementSearch, BorderLayout.EAST);
        return panel(bar, movements);
    }

    private static String noteOf(StockMovement m) {
        String r = m.reason();
        if (Text.blank(r)) {
            return null;
        }
        int dash = r.indexOf(" - ");
        return dash > 0 ? r.substring(dash + 3) : m.type().startsWith("ADJUSTMENT") ? null : r;
    }

    private void loadMovements() {
        movements.setRows(ctx.inventory().movements(Dates.startOf(movementRange.from()),
                Dates.endExclusive(movementRange.to()), null, null));
        movements.filter(movementSearch.getText());
    }

    // ================================================================ categories

    private JComponent buildCategories() {
        categories.setEmptyState(new EmptyState("tag", "No categories yet",
                "Categories group similar products, like Drinks or Snacks. They show as quick filters on the Sales screen.")
                .action("Add a category", "plus", this::addCategory));
        categories.onSelect(c -> updateCategoryButtons());
        categories.onOpen(c -> renameCategory(), false);
        JButton add = Ui.primary("Add category", "plus");
        add.addActionListener(e -> addCategory());
        renameCategory.addActionListener(e -> renameCategory());
        deleteCategory.addActionListener(e -> {
            Category c = categories.selected();
            if (c == null) {
                return;
            }
            String text = c.productCount() == 0 ? "This category has no products."
                    : "Its " + (c.productCount() == 1 ? "product" : c.productCount() + " products")
                    + " will simply have no category. Nothing else changes.";
            if (Msg.confirm(this, "Delete “" + c.name() + "”?", text, "Delete")) {
                safely(() -> {
                    ctx.inventory().deleteCategory(c.id());
                    refresh();
                });
            }
        });
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(Ui.muted("Group similar products so they are quicker to find when selling."), BorderLayout.WEST);
        bar.add(Ui.flowRight(6, deleteCategory, renameCategory, add), BorderLayout.EAST);
        updateCategoryButtons();
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(bar, BorderLayout.NORTH);
        Card c = new Card(new BorderLayout());
        c.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        c.add(categories, BorderLayout.CENTER);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private void updateCategoryButtons() {
        boolean sel = categories.selected() != null;
        renameCategory.setEnabled(sel);
        deleteCategory.setEnabled(sel);
    }

    private void addCategory() {
        String n = Msg.ask(this, "New category", "What should the category be called?", "");
        if (n != null && !n.isBlank()) {
            safely(() -> {
                ctx.inventory().addCategory(n);
                refresh();
            });
        }
    }

    private void renameCategory() {
        Category c = categories.selected();
        if (c == null) {
            return;
        }
        String n = Msg.ask(this, "Rename category", "New name for “" + c.name() + "”:", c.name());
        if (n != null && !n.isBlank()) {
            safely(() -> {
                ctx.inventory().renameCategory(c.id(), n);
                refresh();
            });
        }
    }

    // ================================================================ common

    static JComponent panel(JComponent toolbar, JComponent table) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(toolbar, BorderLayout.NORTH);
        Card c = new Card(new BorderLayout());
        c.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        c.add(table, BorderLayout.CENTER);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    @Override
    public void refresh() {
        switch (tabs.getSelectedIndex()) {
            case 1 -> loadMovements();
            case 2 -> {
                categories.setItems(ctx.inventory().categories());
                updateCategoryButtons();
            }
            default -> {
                loading = true;
                Object selected = categoryFilter.getSelectedItem();
                categoryFilter.removeAllItems();
                categoryFilter.addItem("All categories");
                List<Category> cats = ctx.inventory().categories();
                for (Category c : cats) {
                    categoryFilter.addItem(c);
                    if (selected instanceof Category s && s.id() == c.id()) {
                        selected = c;
                    }
                }
                categoryFilter.setSelectedItem(selected instanceof Category ? selected : "All categories");
                categoryFilter.setVisible(!cats.isEmpty());
                all = ctx.inventory().products(true);
                loading = false;
                updateCounts();
                productCards.show(productHolder, all.isEmpty() ? "empty" : "content");
                applyFilters();
            }
        }
    }

    @Override
    public void focusDefault() {
        if (tabs.getSelectedIndex() == 0 && !all.isEmpty()) {
            search.requestFocusInWindow();
        }
    }

    /** Open Inventory showing what needs restocking: out of stock first, otherwise running low (from the dashboard). */
    public void showLowStock() {
        tabs.setSelectedIndex(0);
        refresh();
        boolean anyOut = all.stream().anyMatch(p -> p.active() && stockStatus(p) == StockStatus.OUT);
        show.select(anyOut ? Show.OUT : Show.LOW);
        applyFilters();
    }
}
