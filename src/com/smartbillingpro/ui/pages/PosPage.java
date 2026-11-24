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
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.Icons;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.PickerDialog;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.CustomerDialog;
import com.smartbillingpro.ui.dialogs.LineEditDialog;
import com.smartbillingpro.ui.dialogs.ProductDialog;
import com.smartbillingpro.ui.dialogs.ReceiptDialog;
import com.smartbillingpro.ui.dialogs.WeightDialog;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The till. Scan or search on the left, the bill and payment on the right.
 * A normal cash sale is: scan items, (optionally type cash received), F12.
 */
public final class PosPage extends Page {

    private final Cart cart = new Cart();
    private final JTextField scan = Ui.search("Scan a barcode or type to search products   (F1)");
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final ButtonGroup chipGroup = new ButtonGroup();
    private Long categoryFilter;
    private final DefaultListModel<Product> gridModel = new DefaultListModel<>();
    private final JList<Product> grid = new JList<>(gridModel);
    private final JLabel gridInfo = Ui.muted(" ");
    private List<Product> allProducts = new ArrayList<>();

    private final DataTable<Cart.Line> cartTable = new DataTable<>();
    private final JButton customerButton = new JButton();
    private final JLabel customerBalance = Ui.pill("", Ui.Tone.NEUTRAL);
    private final JTextField discountField = Ui.field("0");
    private final JToggleButton percentToggle = new JToggleButton("%");
    private final JLabel subtotalValue = valueLabel();
    private final JLabel discountValue = valueLabel();
    private final JLabel taxLabel = Ui.muted("Tax");
    private final JLabel taxValue = valueLabel();
    private final JLabel totalValue = new JLabel();
    private final JLabel itemsLabel = Ui.muted("");
    private final JComboBox<PaymentMethod> method = new JComboBox<>();
    private final JTextField received = Ui.money("Exact amount");
    private final JLabel changeLabel = new JLabel(" ");
    private final JButton completeButton = Ui.success("Complete Sale   F12", "check");
    private final JButton holdButton = Ui.secondary("Hold  F8", "pause");
    private final JButton heldButton = Ui.secondary("Held (0)  F9", "clock");
    private final JButton clearButton = Ui.ghost("Clear", "trash");
    private JPanel taxRow;
    private boolean busy;

    public PosPage(MainFrame frame) {
        super(frame, "Sales", "Scan items, take payment, print the receipt");
        JPanel layout = new JPanel(new BorderLayout(16, 0));
        layout.setOpaque(false);
        layout.add(buildCatalog(), BorderLayout.CENTER);
        layout.add(buildBill(), BorderLayout.EAST);
        setBody(layout);
        installKeys();
        new BarcodeScanner(this, scan, () -> isShowing() && !busy, this::handleCode).install();
    }

    // ================================================================ left: catalogue

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
        scan.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "to-grid");
        scan.getActionMap().put("to-grid", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (gridModel.size() > 0) {
                    grid.setSelectedIndex(0);
                    grid.requestFocusInWindow();
                }
            }
        });
        chips.setOpaque(false);
        JScrollPane chipScroll = new JScrollPane(chips, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        chipScroll.setBorder(BorderFactory.createEmptyBorder());
        chipScroll.setOpaque(false);
        chipScroll.getViewport().setOpaque(false);
        chipScroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 6));
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(scan, BorderLayout.NORTH);
        top.add(chipScroll, BorderLayout.CENTER);
        left.add(top, BorderLayout.NORTH);

        grid.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        grid.setVisibleRowCount(-1);
        grid.setFixedCellWidth(168);
        grid.setFixedCellHeight(108);
        grid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grid.setCellRenderer(new TileRenderer());
        grid.setBackground(Theme.BG);
        grid.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        grid.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = grid.locationToIndex(e.getPoint());
                if (i >= 0 && grid.getCellBounds(i, i).contains(e.getPoint())) {
                    addProduct(gridModel.get(i));
                }
            }
        });
        grid.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "add");
        grid.getActionMap().put("add", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                Product p = grid.getSelectedValue();
                if (p != null) {
                    addProduct(p);
                }
            }
        });
        JScrollPane gs = new JScrollPane(grid);
        gs.setBorder(BorderFactory.createEmptyBorder());
        gs.getViewport().setBackground(Theme.BG);
        gs.getVerticalScrollBar().setUnitIncrement(24);
        left.add(gs, BorderLayout.CENTER);
        left.add(gridInfo, BorderLayout.SOUTH);
        return left;
    }

    private void rebuildChips(List<Category> categories) {
        chips.removeAll();
        for (javax.swing.AbstractButton b : java.util.Collections.list(chipGroup.getElements())) {
            chipGroup.remove(b);
        }
        addChip("All products", null);
        for (Category c : categories) {
            if (c.productCount() > 0) {
                addChip(c.name(), c.id());
            }
        }
        chips.revalidate();
        chips.repaint();
    }

    private void addChip(String text, Long id) {
        JToggleButton b = new JToggleButton(text);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:999;selectedBackground:" + Theme.hex(Theme.ACCENT_INK)
                + ";selectedForeground:#ffffff;background:#ffffff;borderColor:" + Theme.hex(Theme.BORDER)
                + ";hoverBackground:" + Theme.hex(Theme.NAV_HOVER) + ";focusWidth:0;margin:5,14,5,14");
        b.setFont(Theme.bold(13));
        b.setFocusable(false);
        b.setSelected(java.util.Objects.equals(id, categoryFilter));
        b.addActionListener(e -> {
            categoryFilter = id;
            applyFilter();
        });
        chipGroup.add(b);
        chips.add(b);
    }

    private void applyFilter() {
        String q = scan.getText().trim().toLowerCase(Locale.ROOT);
        gridModel.clear();
        int shown = 0;
        for (Product p : allProducts) {
            if (categoryFilter != null && !categoryFilter.equals(p.categoryId())) {
                continue;
            }
            if (!q.isEmpty() && !matches(p, q)) {
                continue;
            }
            gridModel.addElement(p);
            if (++shown >= 400) {
                break;
            }
        }
        gridInfo.setText(allProducts.isEmpty()
                ? "No products yet. Add products in Inventory, or scan a new barcode to register it."
                : shown == 0 ? "No product matches \"" + scan.getText().trim() + "\". Press Enter to look up the barcode."
                : shown + " product(s). Click a product or press ↓ then Enter to add it.");
    }

    private static boolean matches(Product p, String q) {
        if (p.name().toLowerCase(Locale.ROOT).contains(q)) {
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

    /** Product tile in the grid. */
    private final class TileRenderer extends JComponent implements ListCellRenderer<Product> {
        private Product product;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends Product> list, Product value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            product = value;
            selected = isSelected && list.hasFocus();
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() - 8;
            int h = getHeight() - 8;
            List<Variant> vs = product.activeVariants();
            long stock = product.totalStock();
            long min = vs.isEmpty() ? 0 : vs.get(0).minStock();
            StockStatus st = StockStatus.of(stock, min);
            g2.setColor(Theme.SURFACE);
            g2.fillRoundRect(2, 2, w, h, 20, 20);
            g2.setColor(selected ? Theme.ACCENT_INK : Theme.BORDER);
            g2.drawRoundRect(2, 2, w, h, 20, 20);
            g2.setFont(Theme.bold(13));
            g2.setColor(st == StockStatus.OUT ? Theme.MUTED : Theme.TEXT);
            List<String> lines = wrap(g2, product.name(), w - 18, 2);
            int y = 22;
            for (String l : lines) {
                g2.drawString(l, 11, y);
                y += 17;
            }
            g2.setFont(Theme.font(11));
            String badge = vs.size() > 1 ? vs.size() + " options" : st == StockStatus.OUT ? "Out of stock"
                    : (st == StockStatus.LOW ? "Low: " : "") + Qty.format(stock, product.unitCode());
            Ui.Tone tone = st == StockStatus.OUT ? Ui.Tone.BAD : st == StockStatus.LOW ? Ui.Tone.WARN : Ui.Tone.NEUTRAL;
            if (vs.size() > 1 && st != StockStatus.OUT) {
                tone = st == StockStatus.LOW ? Ui.Tone.WARN : Ui.Tone.INFO;
            }
            int bw = g2.getFontMetrics().stringWidth(badge) + 14;
            g2.setColor(tone.bg);
            g2.fillRoundRect(10, h - 50, bw, 19, 19, 19);
            g2.setColor(tone.fg);
            g2.drawString(badge, 17, h - 36);
            g2.setFont(Theme.bold(14));
            g2.setColor(st == StockStatus.OUT ? Theme.MUTED : Theme.TEXT);
            String price;
            if (vs.size() > 1) {
                long lo = vs.stream().mapToLong(Variant::salePrice).min().orElse(0);
                price = "from " + Money.grouped(lo);
            } else {
                price = vs.isEmpty() ? "" : Money.grouped(vs.get(0).salePrice());
            }
            if (product.fractional()) {
                price += " / " + product.unitCode();
            }
            g2.drawString(price, 11, h - 10);
            g2.dispose();
        }

        private List<String> wrap(Graphics2D g2, String text, int width, int maxLines) {
            List<String> out = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                String cand = line.length() == 0 ? word : line + " " + word;
                if (g2.getFontMetrics().stringWidth(cand) <= width) {
                    line.setLength(0);
                    line.append(cand);
                } else {
                    if (line.length() > 0) {
                        out.add(line.toString());
                    }
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (line.length() > 0) {
                out.add(line.toString());
            }
            if (out.size() > maxLines) {
                List<String> cut = new ArrayList<>(out.subList(0, maxLines));
                String last = cut.get(maxLines - 1);
                while (g2.getFontMetrics().stringWidth(last + "…") > width && last.length() > 1) {
                    last = last.substring(0, last.length() - 1);
                }
                cut.set(maxLines - 1, last + "…");
                return cut;
            }
            return out;
        }
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
        Card card = new Card(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        card.setPreferredSize(new Dimension(430, 0));

        // customer
        customerButton.setHorizontalAlignment(SwingConstants.LEFT);
        customerButton.setIcon(Icons.get("user", 16, Theme.TEXT_2));
        customerButton.setToolTipText("Choose customer (F4)");
        customerButton.addActionListener(e -> chooseCustomer());
        JButton walkIn = Ui.icon("close", "Back to walk-in customer");
        walkIn.addActionListener(e -> {
            cart.setCustomer(null);
            refreshCart();
        });
        JPanel cust = new JPanel(new BorderLayout(6, 0));
        cust.setOpaque(false);
        cust.add(customerButton, BorderLayout.CENTER);
        cust.add(Ui.flowRight(4, customerBalance, walkIn), BorderLayout.EAST);
        customerBalance.setVisible(false);
        card.add(cust, BorderLayout.NORTH);

        // cart
        cartTable.text("Item", l -> l.item().displayName(), 190)
                .col("Qty", DataTable.Kind.TEXT, l -> Qty.format(l.qty()) + (l.item().fractional() ? " " + l.item().unitCode() : ""), 60)
                .money("Amount", l -> l.netBeforeCartDiscount());
        cartTable.setEmptyText("Scan or click products to add them");
        cartTable.onOpen(this::editLine);
        cartTable.table().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remove-line");
        cartTable.table().getActionMap().put("remove-line", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                removeLine();
            }
        });
        JButton minus = Ui.icon("minus", "One less (-)");
        minus.addActionListener(e -> step(-1));
        JButton plus = Ui.icon("plus", "One more (+)");
        plus.addActionListener(e -> step(1));
        JButton edit = Ui.icon("edit", "Change quantity, price or discount (F2)");
        edit.addActionListener(e -> editLine(cartTable.selected()));
        JButton remove = Ui.icon("trash", "Remove line (Delete)");
        remove.addActionListener(e -> removeLine());
        JPanel lineTools = new JPanel(new BorderLayout());
        lineTools.setOpaque(false);
        lineTools.add(itemsLabel, BorderLayout.WEST);
        lineTools.add(Ui.flowRight(2, minus, plus, edit, remove), BorderLayout.EAST);
        JPanel cartPanel = new JPanel(new BorderLayout(0, 4));
        cartPanel.setOpaque(false);
        cartTable.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        cartPanel.add(cartTable, BorderLayout.CENTER);
        cartPanel.add(lineTools, BorderLayout.SOUTH);
        card.add(cartPanel, BorderLayout.CENTER);

        // totals + payment
        JPanel south = new JPanel(new GridBagLayout());
        south.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(2, 0, 2, 0);
        south.add(totalRow(Ui.muted("Subtotal"), subtotalValue), c);

        discountField.setColumns(6);
        discountField.setHorizontalAlignment(SwingConstants.RIGHT);
        discountField.setToolTipText("Discount on the whole bill");
        percentToggle.setToolTipText("Switch between amount and percent");
        percentToggle.setFocusable(false);
        percentToggle.addActionListener(e -> applyDiscount());
        Ui.onChange(discountField, this::applyDiscount);
        JPanel disc = new JPanel(new BorderLayout(6, 0));
        disc.setOpaque(false);
        disc.add(Ui.muted("Bill discount"), BorderLayout.WEST);
        disc.add(Ui.flowLeft(4, discountField, percentToggle), BorderLayout.CENTER);
        disc.add(discountValue, BorderLayout.EAST);
        c.gridy++;
        south.add(disc, c);
        taxRow = totalRow(taxLabel, taxValue);
        c.gridy++;
        south.add(taxRow, c);

        Card totalPanel = new Card(new BorderLayout());
        totalPanel.setOutlined(false);
        totalPanel.setBackground(Theme.SURFACE_ALT);
        totalPanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JLabel tl = new JLabel("TOTAL");
        tl.setFont(Theme.bold(15));
        tl.setForeground(Theme.TEXT);
        totalValue.setFont(Theme.heavy(30));
        totalValue.setForeground(Theme.TEXT);
        totalPanel.add(tl, BorderLayout.WEST);
        totalPanel.add(totalValue, BorderLayout.EAST);
        c.gridy++;
        c.insets = new Insets(8, 0, 8, 0);
        south.add(totalPanel, c);
        c.insets = new Insets(2, 0, 2, 0);

        method.setToolTipText("Payment method");
        method.addActionListener(e -> refreshChange());
        received.putClientProperty(FlatClientProperties.STYLE, "font:+3");
        received.setToolTipText("Cash received. Leave empty for the exact amount.");
        Ui.onChange(received, this::refreshChange);
        received.addActionListener(e -> completeSale());
        JPanel pay = new JPanel(new GridBagLayout());
        pay.setOpaque(false);
        GridBagConstraints pc = new GridBagConstraints();
        pc.fill = GridBagConstraints.HORIZONTAL;
        pc.gridy = 0;
        pc.weightx = 0.42;
        pay.add(Ui.row("Payment", method), pc);
        pc.gridx = 1;
        pc.weightx = 0.58;
        pc.insets = new Insets(0, 8, 0, 0);
        pay.add(Ui.row("Amount received   (F10)", received), pc);
        c.gridy++;
        south.add(pay, c);
        changeLabel.setFont(Theme.bold(15));
        changeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        c.gridy++;
        c.insets = new Insets(6, 0, 8, 0);
        south.add(changeLabel, c);
        completeButton.addActionListener(e -> completeSale());
        c.gridy++;
        c.insets = new Insets(2, 0, 6, 0);
        south.add(completeButton, c);
        holdButton.addActionListener(e -> hold());
        heldButton.addActionListener(e -> recall());
        clearButton.addActionListener(e -> clearCart(true));
        JPanel secondary = new JPanel(new java.awt.GridLayout(1, 3, 6, 0));
        secondary.setOpaque(false);
        secondary.add(holdButton);
        secondary.add(heldButton);
        secondary.add(clearButton);
        c.gridy++;
        south.add(secondary, c);
        card.add(south, BorderLayout.SOUTH);
        return card;
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
        Ui.key(this, "F1", () -> focusDefault());
        Ui.key(this, "F2", () -> editLine(cartTable.selected()));
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

    // ================================================================ actions

    @Override
    public void refresh() {
        Settings st = ctx.settings().get();
        allProducts = ctx.inventory().products(false);
        rebuildChips(ctx.inventory().categories());
        applyFilter();
        PaymentMethod selected = (PaymentMethod) method.getSelectedItem();
        method.removeAllItems();
        PaymentMethod def = null;
        for (PaymentMethod m : ctx.settings().paymentMethods(true)) {
            method.addItem(m);
            if (selected != null ? m.id() == selected.id() : st.defaultMethodId() != null && m.id() == st.defaultMethodId()) {
                def = m;
            }
        }
        if (def != null) {
            method.setSelectedItem(def);
        }
        cart.setTaxPercent(st.effectiveTaxPercent());
        taxLabel.setText(st.taxLabel() + " (" + st.effectiveTaxPercent().stripTrailingZeros().toPlainString() + "%)");
        taxRow.setVisible(st.taxEnabled());
        for (Cart.Line l : cart.lines()) {
            SellableItem fresh = ctx.inventory().sellable(l.item().variantId());
            if (fresh != null) {
                cart.refreshItem(fresh);
            }
        }
        refreshCart();
        refreshHeldCount();
    }

    @Override
    public void focusDefault() {
        scan.requestFocusInWindow();
        scan.selectAll();
    }

    /** Enter in the scan box or a scanner burst: exact barcode first, then a unique search hit. */
    private void handleCode(String code) {
        scan.setText("");
        SellableItem item = ctx.inventory().findByCode(code);
        if (item != null) {
            if (!item.active()) {
                Msg.error(this, item.displayName() + " is archived. Restore it in Inventory to sell it.");
                return;
            }
            addItem(item);
            return;
        }
        String q = code.toLowerCase(Locale.ROOT);
        List<Product> hits = allProducts.stream().filter(p -> matches(p, q)).toList();
        if (hits.size() == 1) {
            addProduct(hits.get(0));
            return;
        }
        if (hits.size() > 1) {
            scan.setText(code);
            applyFilter();
            grid.setSelectedIndex(0);
            grid.requestFocusInWindow();
            return;
        }
        boolean looksLikeBarcode = code.matches("[0-9A-Za-z\\-]{4,}") && code.chars().anyMatch(Character::isDigit);
        String text = looksLikeBarcode
                ? "No product has the barcode " + code + ".\n\nWould you like to register a new product with this barcode now?"
                : "No product matches \"" + code + "\".\n\nWould you like to add it as a new product?";
        if (Msg.confirm(this, "Product not found", text, "Register new product")) {
            ProductDialog d = new ProductDialog(this, ctx, null, looksLikeBarcode ? code : null,
                    looksLikeBarcode ? null : code);
            if (d.open(820)) {
                refresh();
                Product p = ctx.inventory().product(d.savedId());
                if (p != null) {
                    addProduct(p);
                }
            }
        }
        focusDefault();
    }

    private void addProduct(Product p) {
        List<SellableItem> variants = ctx.inventory().sellablesForProduct(p.id());
        if (variants.isEmpty()) {
            Msg.error(this, p.name() + " has no active variation to sell.");
            return;
        }
        SellableItem item = variants.get(0);
        if (variants.size() > 1) {
            item = PickerDialog.pick(this, "Choose " + p.name(), variants,
                    v -> v.variantName() + "   —   " + Money.format(v.salePrice()),
                    v -> v.stockQty() <= 0 ? "Out of stock" : "In stock: " + Qty.format(v.stockQty(), v.unitCode())
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
            Cart.Line line = cart.add(fresh, qty);
            refreshCart();
            cartTable.select(line);
            Toolkit.beep(false);
        } catch (AppException e) {
            Toolkit.beep(true);
            Toast.show(this, e.getMessage(), Ui.Tone.BAD);
        }
        focusDefault();
    }

    private void step(int units) {
        Cart.Line l = cartTable.selected();
        if (l == null) {
            if (!cart.lines().isEmpty()) {
                l = cart.lines().get(cart.lines().size() - 1);
            } else {
                return;
            }
        }
        try {
            if (l.item().fractional()) {
                editLine(l);
                return;
            }
            cart.step(l, units);
            refreshCart();
            if (cart.lines().contains(l)) {
                cartTable.select(l);
            }
        } catch (AppException e) {
            Toast.show(this, e.getMessage(), Ui.Tone.BAD);
        }
    }

    private void editLine(Cart.Line l) {
        if (l == null) {
            return;
        }
        if (new LineEditDialog(this, cart, l).open(420)) {
            refreshCart();
            if (cart.lines().contains(l)) {
                cartTable.select(l);
            }
        }
        focusDefault();
    }

    private void removeLine() {
        Cart.Line l = cartTable.selected();
        if (l != null) {
            cart.remove(l);
            refreshCart();
        }
    }

    private void applyDiscount() {
        try {
            String t = discountField.getText().trim();
            if (percentToggle.isSelected()) {
                cart.setDiscountPercent(t.isEmpty() ? BigDecimal.ZERO : new BigDecimal(t));
            } else {
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
        Customer c = PickerDialog.pick(this, "Choose customer", all, Customer::name,
                x -> Text.trim(x.phone()) + (x.balance() > 0 ? "   •   owes " + Money.format(x.balance()) : ""),
                "New customer", () -> {
                    CustomerDialog d = new CustomerDialog(this, ctx, null);
                    return d.open(520) ? ctx.parties().customer(d.savedId()) : null;
                });
        if (c != null) {
            cart.setCustomer(c);
            refreshCart();
        }
        focusDefault();
    }

    private void refreshCart() {
        cartTable.setRows(new ArrayList<>(cart.lines()));
        Customer c = cart.customer();
        if (c == null) {
            customerButton.setText("Walk-in Customer");
            customerBalance.setVisible(false);
        } else {
            Customer fresh = ctx.parties().customer(c.id());
            customerButton.setText(c.name() + (Text.blank(c.phone()) ? "" : "  ·  " + c.phone()));
            long bal = fresh == null ? 0 : fresh.balance();
            customerBalance.setText(bal > 0 ? "Owes " + Money.format(bal) : bal < 0 ? "Advance " + Money.format(-bal) : "No dues");
            Ui.setTone(customerBalance, bal > 0 ? Ui.Tone.BAD : Ui.Tone.GOOD);
            customerBalance.setVisible(true);
        }
        refreshTotals();
    }

    private void refreshTotals() {
        Cart.Totals t = cart.totals();
        subtotalValue.setText(Money.format(t.subtotal()));
        discountValue.setText(t.totalDiscount() > 0 ? "-" + Money.format(t.totalDiscount()) : Money.format(0));
        taxValue.setText(Money.format(t.tax()));
        totalValue.setText(Money.format(t.grandTotal()));
        long units = cart.lines().stream().filter(l -> !l.item().fractional()).mapToLong(Cart.Line::qty).sum();
        itemsLabel.setText(cart.lines().size() + " line(s)" + (units > 0 ? ", " + Qty.format(units) + " item(s)" : ""));
        completeButton.setEnabled(!cart.isEmpty());
        holdButton.setEnabled(!cart.isEmpty());
        refreshChange();
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
            changeLabel.setText("Enter a valid amount");
            return;
        }
        if (paid >= total) {
            changeLabel.setForeground(Theme.GOOD);
            changeLabel.setText(paid == total ? "✓ Exact amount" : "Change to give:  " + Money.format(paid - total));
        } else if (cart.customer() == null) {
            changeLabel.setForeground(Theme.BAD);
            changeLabel.setText("⚠ Short by " + Money.format(total - paid) + " – select a customer for credit");
        } else {
            changeLabel.setForeground(Theme.WARN);
            changeLabel.setText("Balance " + Money.format(total - paid) + " goes to " + cart.customer().name() + "'s account");
        }
    }

    private void completeSale() {
        if (busy || !isShowing()) {
            return;
        }
        if (cart.isEmpty()) {
            Toast.show(this, "The cart is empty. Scan or pick a product first.", Ui.Tone.BAD);
            return;
        }
        PaymentMethod m = (PaymentMethod) method.getSelectedItem();
        if (m == null) {
            Msg.error(this, "Add a payment method in Tools & Settings first.");
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
            received.setText("");
            discountField.setText("");
            percentToggle.setSelected(false);
            refresh();
            String msg = "Sale " + sale.receiptNo() + " saved." + (sale.changeGiven() > 0
                    ? "  Give change: " + Money.format(sale.changeGiven()) : "")
                    + (sale.balanceDue() > 0 ? "  " + Money.format(sale.balanceDue()) + " added to account." : "");
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
                            + "\n\nYou can reprint it from Receipts.");
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
                : Msg.ask(this, "Hold sale", "Name this sale so you can find it later (e.g. customer name):",
                "Held " + Money.format(cart.totals().grandTotal()));
        if (label == null) {
            return;
        }
        safely(() -> {
            ctx.sales().hold(cart, label);
            discountField.setText("");
            refreshCart();
            refreshHeldCount();
            Toast.ok(this, "Sale held. Press F9 to bring it back.");
        });
        focusDefault();
    }

    private void recall() {
        List<HeldSale> held = ctx.sales().heldSales();
        if (held.isEmpty()) {
            Toast.show(this, "There are no held sales.", Ui.Tone.INFO);
            return;
        }
        if (!cart.isEmpty() && !Msg.confirm(this, "Recall held sale",
                "The current cart will be replaced. Hold it first if you still need it.", "Replace cart")) {
            return;
        }
        HeldSale h = PickerDialog.pick(this, "Held sales", held, HeldSale::label,
                x -> x.itemCount() + " line(s)   •   " + Money.format(x.total()) + "   •   held "
                        + com.smartbillingpro.util.Dates.formatDateTime(x.createdAt()), null, null);
        if (h != null) {
            safely(() -> {
                List<String> warnings = ctx.sales().recall(h, cart);
                discountField.setText(cart.discountIsPercent() ? cart.discountPercent().toPlainString()
                        : cart.discountAmount() == 0 ? "" : Money.plain(cart.discountAmount()));
                percentToggle.setSelected(cart.discountIsPercent());
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
        heldButton.setText("Held (" + ctx.sales().heldCount() + ")  F9");
    }

    private void clearCart(boolean ask) {
        if (cart.isEmpty()) {
            return;
        }
        if (ask && !Msg.confirm(this, "Clear cart", "Remove all items from this sale?", "Clear cart")) {
            return;
        }
        cart.clear();
        discountField.setText("");
        received.setText("");
        refreshCart();
        focusDefault();
    }

    @Override
    public boolean canExit() {
        if (cart.isEmpty()) {
            return true;
        }
        Object[] options = {"Hold sale and exit", "Discard sale and exit", "Cancel"};
        int r = javax.swing.JOptionPane.showOptionDialog(this, "There is an unfinished sale in the cart.",
                "Unfinished sale", javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (r == 0) {
            ctx.sales().hold(cart, cart.customer() == null ? "Unfinished sale" : cart.customer().name());
            return true;
        }
        return r == 1;
    }

    /** Small audible feedback for scans (system beep on errors only). */
    private static final class Toolkit {
        static void beep(boolean error) {
            if (error) {
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
        }
    }
}
