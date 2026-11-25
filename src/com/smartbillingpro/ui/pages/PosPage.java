package com.smartbillingpro.ui.pages;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartbillingpro.db.AppException;
import com.smartbillingpro.model.Category;
import com.smartbillingpro.model.Customer;
import com.smartbillingpro.model.HeldSale;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.model.StockStatus;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.service.Cart;
import com.smartbillingpro.service.SaleService;
import com.smartbillingpro.service.Settings;
import com.smartbillingpro.ui.BarcodeScanner;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.CartList;
import com.smartbillingpro.ui.EmptyState;
import com.smartbillingpro.ui.Icons;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PickerDialog;
import com.smartbillingpro.ui.PillGroup;
import com.smartbillingpro.ui.RowList;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.CustomerDialog;
import com.smartbillingpro.ui.dialogs.LineEditDialog;
import com.smartbillingpro.ui.dialogs.ReceiptDialog;
import com.smartbillingpro.ui.dialogs.WeightDialog;
import com.smartbillingpro.ui.flows.ProductFlow;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The till. Find products on the left (scan, type or tap), the bill and payment
 * on the right. A normal cash sale is: scan the items, type the cash received
 * (or leave it empty for the exact amount) and press Complete sale.
 */
public final class PosPage extends Page {

    private static final Category ALL = new Category(-1, "All", 0);

    private final Cart cart = new Cart();

    // ---- products
    private final JTextField scan = Ui.search("Scan a barcode or type a product name");
    private final PillGroup<Category> categories = new PillGroup<>();
    private final JScrollPane categoryScroll;
    private final RowList<Product> products;
    private final JLabel listInfo = Ui.muted(" ");
    private final EmptyState noProducts;
    private final EmptyState emptyCategory;
    private List<Product> allProducts = new ArrayList<>();
    private final Map<Long, Image> thumbs = new HashMap<>();
    private Long categoryFilter;

    // ---- bill
    private final CartList cartList;
    private Cart.Line selectedLine;
    private final JLabel itemsLabel = Ui.muted(" ");
    private final JButton customerButton = new JButton();
    private final JLabel customerBalance = Ui.pill("", Ui.Tone.NEUTRAL);
    private final JButton clearCustomer = Ui.icon("close", "Back to walk-in customer");
    private final JButton addDiscount = Ui.ghost("Add discount", "percent");
    private final JPanel discountRow = new JPanel(new BorderLayout(8, 0));
    private final JTextField discountField = Ui.field("0");
    private final PillGroup<String> discountKind = new PillGroup<>();
    private final JLabel discountValue = valueLabel();
    private final JLabel subtotalValue = valueLabel();
    private final JLabel taxLabel = Ui.muted("Tax");
    private final JLabel taxValue = valueLabel();
    private final JPanel subtotalRow;
    private final JPanel taxRow;
    private final JLabel totalValue = new JLabel();
    private final PillGroup<PaymentMethod> methods = new PillGroup<>();
    private final JLabel receivedLabel = Ui.label("Cash received");
    private final JTextField received = Ui.money("Exact amount");
    private final JPanel quickCash = new JPanel(new GridLayout(1, 0, 6, 0));
    private JPanel receivedRow;
    private JPanel changeRow;
    private int methodCount;
    private final JLabel changeLabel = new JLabel(" ");
    private final JButton completeButton = Ui.success("Complete sale", "check");
    private final JButton holdButton = Ui.ghost("Hold bill", "pause");
    private final JButton heldButton = Ui.ghost("Held bills", "clock");
    private final JButton clearButton = Ui.ghost("Clear", "trash");
    private boolean busy;

    public PosPage(MainFrame frame) {
        super(frame, "Sales", "Scan or tap products, take the money, done");
        products = new RowList<>(this::productRow, 60);
        noProducts = new EmptyState("inventory", "Add your first product",
                "Products you add show up here, ready to sell. Scanning a barcode you haven't added yet also "
                        + "offers to add it.")
                .action("Add a product", "plus", () -> addNewProduct(null, null));
        emptyCategory = new EmptyState("tag", "Nothing in this category", "Choose “All” to see every product.", true);
        cartList = new CartList(new CartList.Actions() {
            @Override
            public void step(Cart.Line line, int units) {
                selectedLine = line;
                PosPage.this.step(units);
            }

            @Override
            public void remove(Cart.Line line) {
                cart.remove(line);
                if (selectedLine == line) {
                    selectedLine = null;
                }
                refreshCart();
                focusDefault();
            }

            @Override
            public void edit(Cart.Line line) {
                editLine(line);
            }

            @Override
            public void select(Cart.Line line) {
                selectedLine = line;
                refreshCartList();
            }
        });
        categoryScroll = new JScrollPane(categories, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        subtotalRow = totalRow(Ui.muted("Subtotal"), subtotalValue);
        taxRow = totalRow(taxLabel, taxValue);
        JButton keys = Ui.ghost("Shortcuts", "keyboard");
        keys.setToolTipText("Keyboard shortcuts for a faster till");
        keys.addActionListener(e -> showShortcuts());
        addAction(keys);
        JPanel layout = new JPanel(new BorderLayout(16, 0));
        layout.setOpaque(false);
        layout.add(buildCatalog(), BorderLayout.CENTER);
        layout.add(buildBill(), BorderLayout.EAST);
        setBody(layout);
        installKeys();
        new BarcodeScanner(this, scan, () -> isShowing() && !busy, this::handleCode).install();
    }

    // ================================================================ left: products

    private JComponent buildCatalog() {
        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        scan.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("barcode", 20, Theme.TEXT_2));
        scan.putClientProperty(FlatClientProperties.STYLE, "font:+3;margin:9,10,9,10");
        scan.addActionListener(e -> {
            String code = scan.getText().trim();
            if (!code.isEmpty()) {
                handleCode(code);
            }
        });
        Ui.onChange(scan, this::applyFilter);
        scan.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "to-list");
        scan.getActionMap().put("to-list", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (products.visibleCount() > 0) {
                    products.jlist().setSelectedIndex(0);
                    products.jlist().requestFocusInWindow();
                }
            }
        });
        products.jlist().getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up-or-search");
        products.jlist().getActionMap().put("up-or-search", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int i = products.jlist().getSelectedIndex();
                if (i <= 0) {
                    focusDefault();
                } else {
                    products.jlist().setSelectedIndex(i - 1);
                    products.jlist().ensureIndexIsVisible(i - 1);
                }
            }
        });
        categories.onChange(c -> {
            categoryFilter = c == null || c.id() < 0 ? null : c.id();
            applyFilter();
        });
        categoryScroll.setBorder(BorderFactory.createEmptyBorder());
        categoryScroll.setOpaque(false);
        categoryScroll.getViewport().setOpaque(false);
        categoryScroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 6));
        categories.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(scan, BorderLayout.NORTH);
        top.add(categoryScroll, BorderLayout.CENTER);
        left.add(top, BorderLayout.NORTH);

        products.setMatcher(PosPage::matches);
        products.setEmptyState(noProducts);
        products.onOpen(p -> addProduct(p), true);
        products.onNoResults(this::offerToAdd);
        Card list = new Card(new BorderLayout());
        list.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        list.add(products, BorderLayout.CENTER);
        left.add(list, BorderLayout.CENTER);
        listInfo.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        left.add(listInfo, BorderLayout.SOUTH);
        return left;
    }

    private RowList.Row productRow(Product p) {
        List<Variant> vs = p.activeVariants();
        long stock = p.totalStock();
        long min = vs.isEmpty() ? 0 : vs.get(0).minStock();
        StockStatus st = StockStatus.of(stock, min);
        String price;
        if (vs.size() > 1) {
            long lo = vs.stream().mapToLong(Variant::salePrice).min().orElse(0);
            long hi = vs.stream().mapToLong(Variant::salePrice).max().orElse(0);
            price = lo == hi ? Money.format(lo) : "from " + Money.format(lo);
        } else {
            price = vs.isEmpty() ? "" : Money.format(vs.get(0).salePrice());
        }
        if (p.fractional()) {
            price += " / " + p.unitCode();
        }
        List<String> sub = new ArrayList<>();
        if (vs.size() > 1) {
            sub.add(vs.size() + " sizes");
        }
        if (p.fractional()) {
            sub.add("sold by " + p.unitCode());
        }
        if (!Text.blank(p.categoryName())) {
            sub.add(p.categoryName());
        }
        String stockText = switch (st) {
            case OUT -> "Out of stock";
            case LOW -> "Only " + Qty.format(Math.max(0, stock), p.unitCode()) + " left";
            default -> Qty.format(stock, p.unitCode()) + " in stock";
        };
        Ui.Tone tone = st == StockStatus.OUT ? Ui.Tone.BAD : st == StockStatus.LOW ? Ui.Tone.WARN : Ui.Tone.NEUTRAL;
        RowList.Row row = RowList.Row.of(p.name(), String.join("  •  ", sub), price).withStatus(stockText, tone)
                .dimmed(st == StockStatus.OUT);
        Image img = thumb(p);
        return img == null ? row : row.withImage(img);
    }

    private Image thumb(Product p) {
        if (!p.hasImage()) {
            return null;
        }
        if (thumbs.containsKey(p.id())) {
            return thumbs.get(p.id());
        }
        Image out = null;
        try {
            byte[] bytes = ctx.inventory().image(p.id());
            BufferedImage src = bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
            if (src != null) {
                int s = 80;
                BufferedImage dst = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = dst.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = Math.max(s / (double) src.getWidth(), s / (double) src.getHeight());
                int w = (int) Math.round(src.getWidth() * scale);
                int h = (int) Math.round(src.getHeight() * scale);
                g.drawImage(src, (s - w) / 2, (s - h) / 2, w, h, null);
                g.dispose();
                out = dst;
            }
        } catch (java.io.IOException | RuntimeException e) {
            out = null;
        }
        thumbs.put(p.id(), out);
        return out;
    }

    private void applyFilter() {
        List<Product> base = categoryFilter == null ? allProducts
                : allProducts.stream().filter(p -> categoryFilter.equals(p.categoryId())).toList();
        products.setEmptyState(allProducts.isEmpty() ? noProducts : emptyCategory);
        products.setItems(base);
        products.filter(scan.getText());
        int shown = products.visibleCount();
        if (allProducts.isEmpty()) {
            listInfo.setText(" ");
        } else if (shown == 0) {
            listInfo.setText(" ");
        } else if (scan.getText().isBlank() && categoryFilter == null) {
            listInfo.setText(allProducts.size() + (allProducts.size() == 1 ? " product" : " products")
                    + "  •  tap one to add it to the bill");
        } else {
            listInfo.setText(shown + " of " + allProducts.size() + " products  •  tap one to add it, or press ↓ then Enter");
        }
    }

    private void offerToAdd(String text) {
        EmptyState none = products.noResultsState();
        none.clearActions();
        if (text.isBlank()) {
            return;
        }
        boolean barcode = looksLikeBarcode(text);
        none.setTitle(barcode ? "No product has this barcode" : "No product called “" + text + "”");
        none.setText(barcode ? "Barcode " + text + " is not in your products yet."
                : "Check the spelling, or add it as a new product.");
        none.action(barcode ? "Add a product with this barcode" : "Add “" + text + "”", "plus",
                () -> addNewProduct(barcode ? text : null, barcode ? null : text));
    }

    private static boolean looksLikeBarcode(String code) {
        return code.matches("[0-9A-Za-z\\-]{4,}") && code.chars().filter(Character::isDigit).count() >= 4
                && !code.contains(" ");
    }

    private static boolean matches(Product p, String q) {
        if (p.name().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        if (p.categoryName() != null && p.categoryName().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        for (Variant v : p.activeVariants()) {
            if ((v.barcode() != null && v.barcode().toLowerCase(Locale.ROOT).contains(q))
                    || (v.sku() != null && v.sku().toLowerCase(Locale.ROOT).contains(q))
                    || v.name().toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private void rebuildCategories(List<Category> all) {
        List<Category> used = new ArrayList<>();
        used.add(ALL);
        for (Category c : all) {
            if (allProducts.stream().anyMatch(p -> Objects.equals(p.categoryId(), c.id()))) {
                used.add(c);
            }
        }
        categories.setItems(used, Category::name);
        Long keep = categoryFilter;
        if (keep != null && used.stream().noneMatch(c -> c.id() == keep)) {
            categoryFilter = null;
        }
        categories.selectWhere(c -> categoryFilter == null ? c.id() < 0 : c.id() == categoryFilter);
        categoryScroll.setVisible(used.size() > 1);
    }

    // ================================================================ right: bill

    private static JLabel valueLabel() {
        JLabel l = new JLabel();
        l.setFont(Theme.font(14));
        l.setForeground(Theme.TEXT);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    private JComponent buildBill() {
        Card card = new Card(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));
        card.setPreferredSize(new Dimension(410, 0));

        // header: title + customer
        JLabel title = new JLabel("Current bill");
        title.setFont(Theme.bold(16));
        title.setForeground(Theme.TEXT);
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(title, BorderLayout.WEST);
        head.add(itemsLabel, BorderLayout.EAST);
        customerButton.setHorizontalAlignment(SwingConstants.LEFT);
        customerButton.setIcon(Icons.get("user", 16, Theme.TEXT_2));
        customerButton.setIconTextGap(8);
        customerButton.setToolTipText("Choose who is buying (F4). Needed to sell on credit.");
        customerButton.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:" + Theme.hex(Theme.SURFACE_ALT)
                + ";borderWidth:0;focusWidth:0;margin:7,12,7,12");
        customerButton.setFont(Theme.bold(13));
        customerButton.addActionListener(e -> chooseCustomer());
        clearCustomer.addActionListener(e -> {
            cart.setCustomer(null);
            refreshCart();
        });
        customerBalance.setVisible(false);
        JPanel cust = new JPanel(new BorderLayout(6, 0));
        cust.setOpaque(false);
        cust.add(customerButton, BorderLayout.CENTER);
        cust.add(Ui.flowRight(4, customerBalance, clearCustomer), BorderLayout.EAST);
        card.add(Ui.vbox(10, head, cust), BorderLayout.NORTH);

        // items
        card.add(cartList, BorderLayout.CENTER);

        // totals + payment
        JPanel south = new JPanel();
        south.setOpaque(false);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));

        addDiscount.setToolTipText("Give money off the whole bill");
        addDiscount.addActionListener(e -> {
            discountRow.setVisible(true);
            addDiscount.setVisible(false);
            discountField.requestFocusInWindow();
            refreshTotals();
        });
        discountField.setColumns(5);
        discountField.setHorizontalAlignment(SwingConstants.RIGHT);
        Ui.onChange(discountField, this::applyDiscount);
        discountField.addActionListener(e -> focusDefault());
        discountKind.setItems(List.of(Money.symbol(), "%"), s -> s);
        discountKind.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
        discountKind.onChange(k -> applyDiscount());
        JButton removeDiscount = Ui.icon("close", "Remove the discount");
        removeDiscount.addActionListener(e -> {
            discountField.setText("");
            discountRow.setVisible(false);
            addDiscount.setVisible(true);
            applyDiscount();
            focusDefault();
        });
        JPanel discLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        discLeft.setOpaque(false);
        JLabel dl = Ui.muted("Discount");
        dl.setFont(Theme.font(14));
        discLeft.add(dl);
        discLeft.add(discountField);
        discLeft.add(discountKind);
        discountRow.setOpaque(false);
        discountRow.add(discLeft, BorderLayout.CENTER);
        discountRow.add(Ui.flowRight(2, discountValue, removeDiscount), BorderLayout.EAST);
        discountRow.setVisible(false);
        JPanel addDiscRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        addDiscRow.setOpaque(false);
        addDiscRow.add(addDiscount);
        add(south, addDiscRow, 0);
        add(south, discountRow, 2);
        add(south, subtotalRow, 2);
        add(south, taxRow, 2);

        Card totalPanel = new Card(new BorderLayout());
        totalPanel.setOutlined(false);
        totalPanel.setBackground(Theme.SURFACE_ALT);
        totalPanel.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        JLabel tl = new JLabel("Total");
        tl.setFont(Theme.bold(16));
        tl.setForeground(Theme.TEXT);
        totalValue.setFont(Theme.heavy(30));
        totalValue.setForeground(Theme.TEXT);
        totalPanel.add(tl, BorderLayout.WEST);
        totalPanel.add(totalValue, BorderLayout.EAST);
        add(south, totalPanel, 8);

        methods.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
        methods.onChange(m -> {
            refreshPaymentMode();
            refreshChange();
        });
        add(south, methods, 10);

        received.putClientProperty(FlatClientProperties.STYLE, "font:+3");
        received.setToolTipText("Leave empty when the customer pays the exact amount (F10)");
        Ui.onChange(received, this::refreshChange);
        received.addActionListener(e -> completeSale());
        JPanel recv = new JPanel(new BorderLayout(10, 0));
        receivedRow = recv;
        recv.setOpaque(false);
        receivedLabel.setPreferredSize(new Dimension(118, 30));
        recv.add(receivedLabel, BorderLayout.WEST);
        recv.add(received, BorderLayout.CENTER);
        add(south, recv, 8);
        quickCash.setOpaque(false);
        add(south, quickCash, 6);
        changeLabel.setFont(Theme.bold(16));
        changeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        changeRow = new JPanel(new BorderLayout());
        changeRow.setOpaque(false);
        changeRow.add(changeLabel, BorderLayout.CENTER);
        add(south, changeRow, 8);

        completeButton.setToolTipText("Save the sale (F12)");
        completeButton.addActionListener(e -> completeSale());
        JPanel completeRow = new JPanel(new BorderLayout());
        completeRow.setOpaque(false);
        completeRow.add(completeButton, BorderLayout.CENTER);
        add(south, completeRow, 8);

        holdButton.setToolTipText("Put this bill aside and serve someone else (F8)");
        heldButton.setToolTipText("Bills you put aside (F9)");
        clearButton.setToolTipText("Remove everything from this bill");
        holdButton.addActionListener(e -> hold());
        heldButton.addActionListener(e -> recall());
        clearButton.addActionListener(e -> clearCart(true));
        JPanel secondary = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        secondary.setOpaque(false);
        secondary.add(holdButton);
        secondary.add(heldButton);
        secondary.add(clearButton);
        add(south, secondary, 6);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    /** Adds a full-width row whose height always follows its content (rows appear and disappear as needed). */
    private static void add(JPanel box, JComponent c, int gapBefore) {
        if (gapBefore > 0 && box.getComponentCount() > 0) {
            Component strut = Box.createVerticalStrut(gapBefore);
            box.add(strut);
            strut.setVisible(c.isVisible());
            c.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentShown(java.awt.event.ComponentEvent e) {
                    strut.setVisible(true);
                }

                @Override
                public void componentHidden(java.awt.event.ComponentEvent e) {
                    strut.setVisible(false);
                }
            });
        }
        JPanel row = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            @Override
            public Dimension getPreferredSize() {
                return c.isVisible() ? super.getPreferredSize() : new Dimension(0, 0);
            }
        };
        row.setOpaque(false);
        row.add(c, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(row);
    }

    private static JPanel totalRow(JLabel label, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        label.setFont(Theme.font(14));
        p.add(label, BorderLayout.WEST);
        p.add(value, BorderLayout.EAST);
        return p;
    }

    private void installKeys() {
        Ui.key(this, "F1", this::focusDefault);
        Ui.key(this, "F2", () -> editLine(currentLine()));
        Ui.key(this, "F4", this::chooseCustomer);
        Ui.key(this, "F8", this::hold);
        Ui.key(this, "F9", this::recall);
        Ui.key(this, "F10", () -> {
            received.requestFocusInWindow();
            received.selectAll();
        });
        Ui.key(this, "F12", this::completeSale);
        Ui.key(this, "ctrl DELETE", () -> clearCart(true));
        Ui.key(this, "ADD", () -> step(1));
        Ui.key(this, "SUBTRACT", () -> step(-1));
        Ui.key(this, "ESCAPE", () -> {
            scan.setText("");
            focusDefault();
        });
    }

    private void showShortcuts() {
        Msg.info(this, "Keyboard shortcuts",
                "F1  –  go to the search box\n"
                        + "↓ then Enter  –  add the highlighted product\n"
                        + "+ / −  (number pad)  –  one more / one less of the last item\n"
                        + "F2  –  change quantity, price or discount of the item\n"
                        + "F4  –  choose the customer\n"
                        + "F10  –  type the cash received\n"
                        + "F12  –  complete the sale\n"
                        + "F8 / F9  –  hold this bill / bring back a held bill\n"
                        + "Ctrl + Delete  –  clear the bill\n"
                        + "Esc  –  clear the search");
    }

    // ================================================================ actions

    @Override
    public void refresh() {
        Settings st = ctx.settings().get();
        allProducts = ctx.inventory().products(false);
        thumbs.clear();
        rebuildCategories(ctx.inventory().categories());
        applyFilter();
        PaymentMethod current = methods.selected();
        List<PaymentMethod> active = ctx.settings().paymentMethods(true);
        methods.setItems(active, PaymentMethod::name);
        if (current != null) {
            methods.selectWhere(m -> m.id() == current.id());
        } else if (st.defaultMethodId() != null) {
            methods.selectWhere(m -> m.id() == st.defaultMethodId());
        }
        methodCount = active.size();
        cart.setTaxPercent(st.effectiveTaxPercent());
        taxLabel.setText(st.taxLabel() + " (" + st.effectiveTaxPercent().stripTrailingZeros().toPlainString() + "%)");
        for (Cart.Line l : cart.lines()) {
            SellableItem fresh = ctx.inventory().sellable(l.item().variantId());
            if (fresh != null) {
                cart.refreshItem(fresh);
            }
        }
        refreshPaymentMode();
        refreshCart();
        refreshHeldCount();
    }

    @Override
    public void focusDefault() {
        scan.requestFocusInWindow();
        scan.selectAll();
    }

    /** Enter in the search box or a scanner burst: exact barcode first, then a single search match. */
    private void handleCode(String code) {
        SellableItem item = ctx.inventory().findByCode(code);
        if (item != null) {
            scan.setText("");
            if (!item.active()) {
                Msg.error(this, item.displayName() + " is hidden. Show it again in Inventory to sell it.");
                return;
            }
            addItem(item);
            return;
        }
        String q = code.toLowerCase(Locale.ROOT);
        List<Product> hits = allProducts.stream().filter(p -> matches(p, q)).toList();
        if (hits.size() == 1) {
            scan.setText("");
            addProduct(hits.get(0));
            return;
        }
        if (hits.size() > 1) {
            scan.setText(code);
            applyFilter();
            products.selectFirst();
            products.jlist().requestFocusInWindow();
            return;
        }
        boolean barcode = looksLikeBarcode(code);
        String text = barcode
                ? "No product has the barcode " + code + " yet. Would you like to add it now? It takes a few seconds."
                : "No product is called “" + code + "”. Would you like to add it as a new product?";
        if (Msg.confirm(this, barcode ? "New barcode" : "Product not found", text, "Add product")) {
            scan.setText("");
            addNewProduct(barcode ? code : null, barcode ? null : code);
        } else {
            scan.setText(barcode ? "" : code);
            focusDefault();
        }
    }

    /** Opens the step-by-step "Add a product" flow and puts the new product on the bill. */
    private void addNewProduct(String barcode, String name) {
        ProductFlow.Result r = ProductFlow.add(this, ctx, barcode, name);
        scan.setText("");
        refresh();
        if (r.productId() != null) {
            Product p = ctx.inventory().product(r.productId());
            if (p != null && p.totalStock() > 0) {
                addProduct(p);
            } else if (p != null) {
                Toast.ok(this, p.name() + " added. It has no stock yet – tap it to sell anyway.");
            }
        }
        focusDefault();
    }

    private void addProduct(Product p) {
        List<SellableItem> variants = ctx.inventory().sellablesForProduct(p.id());
        if (variants.isEmpty()) {
            Msg.error(this, p.name() + " has nothing to sell. Open it in Inventory to check its sizes.");
            return;
        }
        SellableItem item = variants.get(0);
        if (variants.size() > 1) {
            item = PickerDialog.pick(this, "Which " + p.name() + "?", variants,
                    v -> v.variantName() + "   —   " + Money.format(v.salePrice()),
                    v -> v.stockQty() <= 0 ? "Out of stock" : Qty.format(v.stockQty(), v.unitCode()) + " in stock"
                            + (v.barcode() == null ? "" : "   •   " + v.barcode()), null, null);
            if (item == null) {
                focusDefault();
                return;
            }
        }
        addItem(item);
    }

    private void addItem(SellableItem item) {
        try {
            SellableItem fresh = ctx.inventory().sellable(item.variantId());
            long qty = Qty.ONE;
            if (fresh.fractional()) {
                Long measured = WeightDialog.ask(this, ctx, fresh);
                if (measured == null) {
                    focusDefault();
                    return;
                }
                qty = measured;
            }
            selectedLine = cart.add(fresh, qty);
            refreshCart();
        } catch (AppException e) {
            java.awt.Toolkit.getDefaultToolkit().beep();
            Toast.show(this, e.getMessage(), Ui.Tone.BAD);
        }
        focusDefault();
    }

    private Cart.Line currentLine() {
        if (selectedLine != null && cart.lines().contains(selectedLine)) {
            return selectedLine;
        }
        return cart.lines().isEmpty() ? null : cart.lines().get(cart.lines().size() - 1);
    }

    private void step(int units) {
        Cart.Line l = currentLine();
        if (l == null) {
            return;
        }
        try {
            if (l.item().fractional()) {
                editLine(l);
                return;
            }
            cart.step(l, units);
            selectedLine = cart.lines().contains(l) ? l : null;
            refreshCart();
        } catch (AppException e) {
            Toast.show(this, e.getMessage(), Ui.Tone.BAD);
        }
    }

    private void editLine(Cart.Line l) {
        if (l == null) {
            return;
        }
        if (new LineEditDialog(this, cart, l).open(420)) {
            selectedLine = cart.lines().contains(l) ? l : null;
            refreshCart();
        }
        focusDefault();
    }

    private void applyDiscount() {
        try {
            String t = discountField.getText().trim();
            if ("%".equals(discountKind.selected())) {
                cart.setDiscountAmount(0);
                cart.setDiscountPercent(t.isEmpty() ? BigDecimal.ZERO : new BigDecimal(t));
            } else {
                cart.setDiscountPercent(BigDecimal.ZERO);
                cart.setDiscountAmount(t.isEmpty() ? 0 : Money.parse(t));
            }
            Ui.setError(discountField, false);
        } catch (RuntimeException e) {
            Ui.setError(discountField, true);
        }
        refreshTotals();
    }

    private void chooseCustomer() {
        List<Customer> all = ctx.parties().customers(false);
        Customer c = PickerDialog.pick(this, "Who is buying?", all, Customer::name,
                x -> Text.trim(x.phone()) + (x.balance() > 0 ? "   •   owes " + Money.format(x.balance()) : ""),
                "New customer", () -> {
                    CustomerDialog d = new CustomerDialog(this, ctx, null);
                    return d.open(480) ? ctx.parties().customer(d.savedId()) : null;
                });
        if (c != null) {
            cart.setCustomer(c);
            refreshCart();
        }
        focusDefault();
    }

    private void refreshCartList() {
        cartList.setLines(new ArrayList<>(cart.lines()), currentLine());
    }

    private void refreshCart() {
        refreshCartList();
        Customer c = cart.customer();
        if (c == null) {
            customerButton.setText("Walk-in customer");
            customerButton.setForeground(Theme.TEXT_2);
            customerBalance.setVisible(false);
            clearCustomer.setVisible(false);
        } else {
            Customer fresh = ctx.parties().customer(c.id());
            customerButton.setText(c.name());
            customerButton.setForeground(Theme.TEXT);
            long bal = fresh == null ? 0 : fresh.balance();
            customerBalance.setText(bal > 0 ? "Owes " + Money.format(bal) : bal < 0 ? "Paid ahead " + Money.format(-bal)
                    : "Nothing owed");
            Ui.setTone(customerBalance, bal > 0 ? Ui.Tone.WARN : Ui.Tone.GOOD);
            customerBalance.setVisible(true);
            clearCustomer.setVisible(true);
        }
        refreshTotals();
    }

    private void refreshTotals() {
        Cart.Totals t = cart.totals();
        boolean hasDiscount = discountRow.isVisible();
        boolean tax = ctx.settings().get().taxEnabled();
        subtotalValue.setText(Money.format(t.subtotal()));
        discountValue.setText(t.totalDiscount() > 0 ? "−" + Money.format(t.totalDiscount()) : Money.format(0));
        taxValue.setText(Money.format(t.tax()));
        totalValue.setText(Money.format(t.grandTotal()));
        subtotalRow.setVisible(!cart.isEmpty() && (hasDiscount || tax || t.itemDiscount() > 0));
        taxRow.setVisible(tax);
        long units = cart.lines().stream().filter(l -> !l.item().fractional()).mapToLong(Cart.Line::qty).sum();
        int lines = cart.lines().size();
        itemsLabel.setText(lines == 0 ? " " : units > 0 && units / Qty.ONE != lines
                ? Qty.format(units) + " items" : lines == 1 ? "1 item" : lines + " items");
        completeButton.setEnabled(!cart.isEmpty());
        completeButton.setText(cart.isEmpty() ? "Complete sale" : "Complete sale  •  " + Money.format(t.grandTotal()));
        boolean paying = !cart.isEmpty();
        methods.setVisible(paying && methodCount > 1);
        receivedRow.setVisible(paying);
        changeRow.setVisible(paying);
        holdButton.setEnabled(!cart.isEmpty());
        clearButton.setEnabled(!cart.isEmpty());
        refreshQuickCash(t.grandTotal());
        refreshChange();
    }

    private boolean cashSelected() {
        PaymentMethod m = methods.selected();
        return m == null || m.cash();
    }

    private void refreshPaymentMode() {
        boolean cash = cashSelected();
        receivedLabel.setText(cash ? "Cash received" : "Amount paid");
        received.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, cash ? "Exact amount" : "Full amount");
        refreshQuickCash(cart.totals().grandTotal());
    }

    /** "Exact" plus the next round amounts a customer is likely to hand over. */
    private void refreshQuickCash(long total) {
        quickCash.removeAll();
        boolean show = !cart.isEmpty() && cashSelected() && total > 0;
        quickCash.setVisible(show);
        if (!show) {
            return;
        }
        JButton exact = quick("Exact", () -> received.setText(""));
        quickCash.add(exact);
        long unit = Money.factor();
        TreeSet<Long> amounts = new TreeSet<>();
        for (long note : new long[] {100, 500, 1000, 5000}) {
            long step = note * unit;
            long v = ((total + step - 1) / step) * step;
            if (v > total) {
                amounts.add(v);
            }
        }
        int n = 0;
        for (long v : amounts) {
            if (n++ >= 3) {
                break;
            }
            quickCash.add(quick(Money.grouped(v), () -> received.setText(Money.plain(v))));
        }
        quickCash.revalidate();
        quickCash.repaint();
    }

    private JButton quick(String text, Runnable r) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:#ffffff;borderColor:"
                + Theme.hex(Theme.BORDER_STRONG) + ";hoverBackground:" + Theme.hex(Theme.NAV_HOVER)
                + ";focusWidth:0;margin:5,6,5,6");
        b.setFont(Theme.bold(13));
        b.setFocusable(false);
        b.addActionListener(e -> {
            r.run();
            refreshChange();
        });
        return b;
    }

    private void refreshChange() {
        long total = cart.totals().grandTotal();
        String t = received.getText().trim();
        if (cart.isEmpty()) {
            changeLabel.setText(" ");
            return;
        }
        long paid;
        try {
            paid = t.isEmpty() ? total : Money.parse(t);
            Ui.setError(received, false);
        } catch (IllegalArgumentException e) {
            Ui.setError(received, true);
            changeLabel.setForeground(Theme.BAD);
            changeLabel.setText("Please type a valid amount");
            return;
        }
        if (paid > total) {
            changeLabel.setForeground(Theme.GOOD);
            changeLabel.setText("Give back  " + Money.format(paid - total));
        } else if (paid == total) {
            changeLabel.setForeground(Theme.MUTED);
            changeLabel.setFont(Theme.bold(14));
            changeLabel.setText(t.isEmpty() ? "Customer pays the exact amount" : "✓ Exact amount");
            return;
        } else if (cart.customer() == null) {
            changeLabel.setForeground(Theme.BAD);
            changeLabel.setText("<html><div style='text-align:right'>" + Money.format(total - paid)
                    + " short – choose the customer to put it on their account</div></html>");
        } else {
            changeLabel.setForeground(Theme.WARN);
            changeLabel.setText("<html><div style='text-align:right'>" + Money.format(total - paid) + " will be added to "
                    + cart.customer().name().replace("<", "&lt;") + "'s account</div></html>");
        }
        changeLabel.setFont(Theme.bold(16));
    }

    private void completeSale() {
        if (busy || !isShowing()) {
            return;
        }
        if (cart.isEmpty()) {
            Toast.show(this, "The bill is empty. Scan or tap a product first.", Ui.Tone.BAD);
            return;
        }
        PaymentMethod m = methods.selected();
        if (m == null) {
            Msg.error(this, "Add a way to pay (e.g. Cash) in Tools & Settings first.");
            return;
        }
        long total = cart.totals().grandTotal();
        long paid;
        try {
            paid = received.getText().isBlank() ? total : Money.parse(received.getText());
        } catch (IllegalArgumentException e) {
            Msg.error(this, e.getMessage());
            received.requestFocusInWindow();
            return;
        }
        busy = true;
        completeButton.setEnabled(false);
        try {
            Sale sale = ctx.sales().complete(cart, new SaleService.PaymentInput(m.id(), paid, null));
            cart.clear();
            selectedLine = null;
            received.setText("");
            discountField.setText("");
            discountRow.setVisible(false);
            addDiscount.setVisible(true);
            refresh();
            String msg = "Sale " + sale.receiptNo() + " saved." + (sale.changeGiven() > 0
                    ? "  Give back " + Money.format(sale.changeGiven()) + "." : "")
                    + (sale.balanceDue() > 0 ? "  " + Money.format(sale.balanceDue()) + " added to the account." : "");
            Toast.ok(this, msg);
            afterSale(sale);
        } catch (RuntimeException e) {
            Msg.error(this, e);
            refresh();
        } finally {
            busy = false;
            completeButton.setEnabled(!cart.isEmpty());
            focusDefault();
        }
    }

    private void afterSale(Sale sale) {
        Settings st = ctx.settings().get();
        if (st.autoPdf()) {
            try {
                Path dir = st.pdfDir().isBlank() ? ctx.paths().defaultExportDir().resolve("Receipts") : Path.of(st.pdfDir());
                ReceiptDialog.savePdf(ctx, sale, dir.resolve(sale.receiptNo() + ".pdf"));
            } catch (RuntimeException | java.io.IOException e) {
                Toast.show(this, "Receipt PDF could not be saved: " + e.getMessage(), Ui.Tone.BAD);
            }
        }
        switch (st.afterSale()) {
            case PRINT -> {
                try {
                    ReceiptDialog.print(ctx, sale);
                } catch (RuntimeException e) {
                    Msg.error(this, "The sale is saved, but the receipt could not be printed:\n" + e.getMessage()
                            + "\n\nYou can print it again from Receipts.");
                }
            }
            case PREVIEW -> new ReceiptDialog(this, ctx, sale).open(560);
            default -> {
            }
        }
    }

    private void hold() {
        if (cart.isEmpty()) {
            return;
        }
        String label = cart.customer() != null ? cart.customer().name()
                : Msg.ask(this, "Hold this bill", "Give it a name so you can find it again (e.g. the customer's name):",
                "Bill " + Money.format(cart.totals().grandTotal()));
        if (label == null) {
            return;
        }
        safely(() -> {
            ctx.sales().hold(cart, label);
            selectedLine = null;
            discountField.setText("");
            discountRow.setVisible(false);
            addDiscount.setVisible(true);
            received.setText("");
            refreshCart();
            refreshHeldCount();
            Toast.ok(this, "Bill put on hold. Find it under “Held bills”.");
        });
        focusDefault();
    }

    private void recall() {
        List<HeldSale> held = ctx.sales().heldSales();
        if (held.isEmpty()) {
            Toast.show(this, "There are no bills on hold.", Ui.Tone.INFO);
            return;
        }
        if (!cart.isEmpty() && !Msg.confirm(this, "Bring back a held bill",
                "The current bill will be replaced. Hold it first if you still need it.", "Replace bill")) {
            return;
        }
        HeldSale h = PickerDialog.pick(this, "Held bills", held, HeldSale::label,
                x -> (x.itemCount() == 1 ? "1 item" : x.itemCount() + " items") + "   •   " + Money.format(x.total())
                        + "   •   " + com.smartbillingpro.util.Dates.formatDateTime(x.createdAt()), null, null);
        if (h != null) {
            safely(() -> {
                List<String> warnings = ctx.sales().recall(h, cart);
                selectedLine = null;
                boolean hasDiscount = cart.discountIsPercent() ? cart.discountPercent().signum() > 0
                        : cart.discountAmount() > 0;
                discountKind.select(cart.discountIsPercent() ? "%" : Money.symbol());
                discountField.setText(!hasDiscount ? "" : cart.discountIsPercent()
                        ? cart.discountPercent().stripTrailingZeros().toPlainString() : Money.plain(cart.discountAmount()));
                discountRow.setVisible(hasDiscount);
                addDiscount.setVisible(!hasDiscount);
                refreshCart();
                refreshHeldCount();
                if (!warnings.isEmpty()) {
                    Msg.info(this, "Some items changed", String.join("\n", warnings));
                }
            });
        }
        focusDefault();
    }

    private void refreshHeldCount() {
        long n = ctx.sales().heldCount();
        heldButton.setText(n == 0 ? "Held bills" : "Held bills (" + n + ")");
        heldButton.setVisible(n > 0);
    }

    private void clearCart(boolean ask) {
        if (cart.isEmpty()) {
            return;
        }
        if (ask && !Msg.confirm(this, "Clear the bill?", "All items will be removed from this bill.", "Clear bill")) {
            return;
        }
        cart.clear();
        selectedLine = null;
        discountField.setText("");
        discountRow.setVisible(false);
        addDiscount.setVisible(true);
        received.setText("");
        refreshCart();
        focusDefault();
    }

    @Override
    public boolean canExit() {
        if (cart.isEmpty()) {
            return true;
        }
        int r = Msg.choose(this, "Unfinished bill", "There is an unfinished bill. What should happen to it?",
                "Hold it and exit", "Throw it away and exit", "Stay here");
        if (r == 0) {
            ctx.sales().hold(cart, cart.customer() == null ? "Unfinished bill" : cart.customer().name());
            return true;
        }
        return r == 1;
    }
}
