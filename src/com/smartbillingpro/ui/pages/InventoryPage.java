package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.Category;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.StockMovement;
import com.smartbillingpro.model.StockStatus;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.ProductDialog;
import com.smartbillingpro.ui.dialogs.StockAdjustDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.List;
import java.util.stream.Collectors;

/** Products with variations, categories and the full stock movement history. */
public final class InventoryPage extends Page {

    private final JTabbedPane tabs = new JTabbedPane();
    // products
    private final JTextField search = Ui.search("Search name, barcode or SKU");
    private final JComboBox<Object> categoryFilter = new JComboBox<>();
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[] {"All active products", "Low stock",
        "Out of stock", "Archived"});
    private final DataTable<Product> products = new DataTable<>();
    private final JButton edit = Ui.secondary("Edit", "edit");
    private final JButton adjust = Ui.secondary("Adjust stock", "inventory");
    private final JButton archive = Ui.ghost("Archive", "archive");
    private final JButton delete = Ui.ghost("Delete", "trash");
    private List<Product> all = List.of();
    // categories
    private final DataTable<Category> categories = new DataTable<>();
    // movements
    private final DateRangeBar movementRange = new DateRangeBar(DateRangeBar.Preset.LAST_30);
    private final JTextField movementSearch = Ui.search("Search product, reference or reason");
    private final DataTable<StockMovement> movements = new DataTable<>();
    private boolean loading;

    public InventoryPage(MainFrame frame) {
        super(frame, "Inventory", "Products, stock levels and categories");
        JButton add = Ui.primary("Add product", "plus");
        add.addActionListener(e -> openProduct(null));
        addAction(add);
        tabs.addTab("Products", buildProducts());
        tabs.addTab("Categories", buildCategories());
        tabs.addTab("Stock movements", buildMovements());
        tabs.addChangeListener(e -> refresh());
        setBody(tabs);
        Ui.key(this, "ctrl N", () -> {
            if (isShowing()) {
                openProduct(null);
            }
        });
    }

    // ------------------------------------------------------------ products

    private JComponent buildProducts() {
        products.text("Product", p -> p.name() + (p.hasVariants() ? "  (" + p.activeVariants().size() + " variations)" : ""), 260)
                .text("Category", p -> Text.orDash(p.categoryName()), 120)
                .text("Barcode", p -> p.hasVariants() ? "—" : Text.orDash(p.firstVariant() == null ? null : p.firstVariant().barcode()), 120)
                .money("Cost", p -> p.hasVariants() ? null : first(p).costPrice())
                .text("Price", p -> priceText(p), 110)
                .col("Stock", DataTable.Kind.TEXT, p -> Qty.format(p.totalStock(), p.unitCode()), 90)
                .status("Status", this::status)
                .money("Stock value", p -> p.activeVariants().stream().mapToLong(v -> Money.mulQty(v.costPrice(), v.stockQty())).sum());
        products.table().getColumnModel().getColumn(4).setCellRenderer(rightRenderer());
        products.setEmptyText("No products yet. Click \"Add product\" or scan a new barcode on the Sales screen.");
        products.onOpen(p -> openProduct(p.id()));
        products.onSelect(p -> updateButtons());
        Ui.onChange(search, () -> products.filter(search.getText()));
        categoryFilter.addActionListener(e -> applyFilters());
        statusFilter.addActionListener(e -> applyFilters());
        edit.addActionListener(e -> {
            Product p = products.selected();
            if (p != null) {
                openProduct(p.id());
            }
        });
        adjust.addActionListener(e -> adjustStock());
        archive.addActionListener(e -> toggleArchive());
        delete.addActionListener(e -> deleteProduct());
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);
        search.setColumns(22);
        bar.add(Ui.flowLeft(8, search, categoryFilter, statusFilter), BorderLayout.WEST);
        bar.add(Ui.flowRight(6, edit, adjust, archive, delete), BorderLayout.EAST);
        return panel(bar, products);
    }

    private static javax.swing.table.DefaultTableCellRenderer rightRenderer() {
        javax.swing.table.DefaultTableCellRenderer r = new javax.swing.table.DefaultTableCellRenderer();
        r.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        r.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        return r;
    }

    private static Variant first(Product p) {
        List<Variant> a = p.activeVariants();
        return a.isEmpty() ? p.firstVariant() : a.get(0);
    }

    private static String priceText(Product p) {
        List<Variant> vs = p.activeVariants();
        if (vs.isEmpty()) {
            return "—";
        }
        long lo = vs.stream().mapToLong(Variant::salePrice).min().orElse(0);
        long hi = vs.stream().mapToLong(Variant::salePrice).max().orElse(0);
        String s = lo == hi ? Money.grouped(lo) : Money.grouped(lo) + " – " + Money.grouped(hi);
        return p.fractional() ? s + " /" + p.unitCode() : s;
    }

    private StockStatus stockStatus(Product p) {
        StockStatus worst = StockStatus.IN_STOCK;
        for (Variant v : p.activeVariants()) {
            StockStatus s = StockStatus.of(v.stockQty(), v.minStock());
            if (s.ordinal() > worst.ordinal()) {
                worst = s;
            }
        }
        return p.activeVariants().isEmpty() ? StockStatus.OUT : worst;
    }

    private DataTable.Status status(Product p) {
        if (!p.active()) {
            return new DataTable.Status("Archived", Ui.Tone.NEUTRAL);
        }
        StockStatus s = stockStatus(p);
        String label = s.label();
        if (p.hasVariants() && s != StockStatus.IN_STOCK) {
            label = s == StockStatus.OUT ? "Some out" : "Some low";
        }
        return new DataTable.Status(label, s == StockStatus.OUT ? Ui.Tone.BAD : s == StockStatus.LOW ? Ui.Tone.WARN : Ui.Tone.GOOD);
    }

    private void applyFilters() {
        if (loading) {
            return;
        }
        Object cat = categoryFilter.getSelectedItem();
        int st = statusFilter.getSelectedIndex();
        List<Product> rows = all.stream()
                .filter(p -> !(cat instanceof Category c) || java.util.Objects.equals(c.id(), p.categoryId()))
                .filter(p -> switch (st) {
                    case 1 -> p.active() && stockStatus(p) == StockStatus.LOW;
                    case 2 -> p.active() && stockStatus(p) == StockStatus.OUT;
                    case 3 -> !p.active();
                    default -> p.active();
                })
                .collect(Collectors.toList());
        products.setRows(rows);
        products.filter(search.getText());
        long value = rows.stream().filter(Product::active).flatMap(p -> p.activeVariants().stream())
                .mapToLong(v -> Money.mulQty(v.costPrice(), v.stockQty())).sum();
        setSubtitle(rows.size() + " product(s) shown  •  stock value at cost " + Money.format(value));
        updateButtons();
    }

    private void updateButtons() {
        Product p = products.selected();
        edit.setEnabled(p != null);
        adjust.setEnabled(p != null && p.active());
        archive.setEnabled(p != null);
        archive.setText(p != null && !p.active() ? "Restore" : "Archive");
        delete.setEnabled(p != null);
    }

    private void openProduct(Long id) {
        ProductDialog d = new ProductDialog(this, ctx, id, null, null);
        if (d.open(820)) {
            Toast.ok(this, id == null ? "Product added." : "Product saved.");
            refresh();
            products.rows().stream().filter(p -> p.id() == d.savedId()).findFirst().ifPresent(products::select);
        }
    }

    private void adjustStock() {
        Product p = products.selected();
        if (p == null) {
            return;
        }
        if (new StockAdjustDialog(this, ctx, p).open(560)) {
            Toast.ok(this, "Stock updated and recorded.");
            refresh();
        }
    }

    private void toggleArchive() {
        Product p = products.selected();
        if (p == null) {
            return;
        }
        if (p.active() && !Msg.confirm(this, "Archive product", "Archive \"" + p.name() + "\"? It will be hidden from "
                + "the Sales screen but its history stays in reports. You can restore it any time.", "Archive")) {
            return;
        }
        safely(() -> {
            ctx.inventory().setArchived(p.id(), p.active());
            Toast.ok(this, p.active() ? "Product archived." : "Product restored.");
            refresh();
        });
    }

    private void deleteProduct() {
        Product p = products.selected();
        if (p == null) {
            return;
        }
        if (!ctx.inventory().canDelete(p.id())) {
            Msg.info(this, "Cannot delete", "\"" + p.name() + "\" has stock or sales history, so it cannot be deleted. "
                    + "Archive it instead - it will disappear from the Sales screen but reports stay correct.");
            return;
        }
        if (Msg.confirm(this, "Delete product", "Permanently delete \"" + p.name() + "\"? It has never been sold or stocked.",
                "Delete")) {
            safely(() -> {
                ctx.inventory().delete(p.id());
                Toast.ok(this, "Product deleted.");
                refresh();
            });
        }
    }

    // ------------------------------------------------------------ categories

    private JComponent buildCategories() {
        categories.text("Category", Category::name, 300).col("Products", DataTable.Kind.INT, c -> (long) c.productCount(), 100);
        categories.setEmptyText("No categories yet. Add some to group products and filter the Sales screen.");
        JButton add = Ui.secondary("Add category", "plus");
        add.addActionListener(e -> {
            String n = Msg.ask(this, "New category", "Category name:", "");
            if (n != null) {
                safely(() -> {
                    ctx.inventory().addCategory(n);
                    refresh();
                });
            }
        });
        JButton rename = Ui.secondary("Rename", "edit");
        rename.addActionListener(e -> {
            Category c = categories.selected();
            if (c == null) {
                return;
            }
            String n = Msg.ask(this, "Rename category", "New name:", c.name());
            if (n != null) {
                safely(() -> {
                    ctx.inventory().renameCategory(c.id(), n);
                    refresh();
                });
            }
        });
        categories.onOpen(c -> rename.doClick());
        JButton del = Ui.ghost("Delete", "trash");
        del.addActionListener(e -> {
            Category c = categories.selected();
            if (c != null && Msg.confirm(this, "Delete category", "Delete the category \"" + c.name() + "\"?", "Delete")) {
                safely(() -> {
                    ctx.inventory().deleteCategory(c.id());
                    refresh();
                });
            }
        });
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(Ui.muted("Categories organise products and power the filters on the Sales screen."), BorderLayout.WEST);
        bar.add(Ui.flowRight(6, add, rename, del), BorderLayout.EAST);
        return panel(bar, categories);
    }

    // ------------------------------------------------------------ movements

    private JComponent buildMovements() {
        movements.col("Date", DataTable.Kind.DATETIME, StockMovement::createdAt, 150)
                .text("Product", StockMovement::displayName, 220)
                .text("Type", StockMovement::typeLabel, 130)
                .col("Change", DataTable.Kind.TEXT, m -> (m.qtyChange() > 0 ? "+" : "") + Qty.format(m.qtyChange()), 80)
                .col("Balance", DataTable.Kind.TEXT, m -> Qty.format(m.balanceAfter(), m.unitCode()), 90)
                .text("Reference", m -> Text.orDash(m.reference()), 110)
                .text("Reason", m -> Text.orDash(m.reason()), 220);
        movements.table().getColumnModel().getColumn(3).setCellRenderer(rightRenderer());
        movements.table().getColumnModel().getColumn(4).setCellRenderer(rightRenderer());
        movements.setEmptyText("No stock changes in this period.");
        movementRange.onChange(this::loadMovements);
        Ui.onChange(movementSearch, () -> movements.filter(movementSearch.getText()));
        movementSearch.setColumns(22);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(movementRange, BorderLayout.WEST);
        bar.add(Ui.flowRight(0, movementSearch), BorderLayout.EAST);
        return panel(bar, movements);
    }

    private void loadMovements() {
        movements.setRows(ctx.inventory().movements(Dates.startOf(movementRange.from()),
                Dates.endExclusive(movementRange.to()), null, null));
        movements.filter(movementSearch.getText());
    }

    // ------------------------------------------------------------ common

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
            case 1 -> categories.setRows(ctx.inventory().categories());
            case 2 -> loadMovements();
            default -> {
                loading = true;
                Object selected = categoryFilter.getSelectedItem();
                categoryFilter.removeAllItems();
                categoryFilter.addItem("All categories");
                for (Category c : ctx.inventory().categories()) {
                    categoryFilter.addItem(c);
                    if (selected instanceof Category s && s.id() == c.id()) {
                        selected = c;
                    }
                }
                categoryFilter.setSelectedItem(selected instanceof Category ? selected : "All categories");
                all = ctx.inventory().products(true);
                loading = false;
                applyFilters();
            }
        }
    }

    /** Open Inventory filtered to low / out of stock (used from the dashboard). */
    public void showLowStock() {
        tabs.setSelectedIndex(0);
        statusFilter.setSelectedIndex(1);
    }
}
