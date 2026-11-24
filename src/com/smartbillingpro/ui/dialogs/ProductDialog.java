package com.smartbillingpro.ui.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Category;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.model.Unit;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Icons;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Add or edit a product, with optional variations (sizes, colours...) and weight-based units. */
public final class ProductDialog extends FormDialog {

    private final AppContext ctx;
    private final Product existing;
    private long savedId;

    private final JTextField name = Ui.field("e.g. Basmati Rice, Coca Cola 1.5L");
    private final JComboBox<Object> category = new JComboBox<>();
    private final JComboBox<Object> supplier = new JComboBox<>();
    private final JTextArea description = new JTextArea(2, 20);
    private final JComboBox<Unit> unit = new JComboBox<>();
    private final JTextField weight = Ui.field("optional");
    private final JComboBox<String> weightUnit = new JComboBox<>(new String[] {"g", "kg", "ml", "L"});
    private final JCheckBox hasVariants = new JCheckBox("This product comes in variations (sizes, colours, packs…)");

    // simple product
    private final JTextField barcode = Ui.field("Scan or type the barcode");
    private final JLabel barcodeStatus = Ui.muted(" ");
    private final JTextField sku = Ui.field("optional");
    private final JTextField cost = Ui.money("0.00");
    private final JTextField price = Ui.money("0.00");
    private final JTextField stock = Ui.field("0");
    private final JTextField minStock = Ui.field("0");
    private final JLabel margin = Ui.muted(" ");
    private final JLabel priceUnit = Ui.muted("");

    // variations
    private final DefaultTableModel variantModel = new DefaultTableModel(
            new Object[] {"Variation", "Barcode", "SKU", "Cost", "Price", "Stock", "Min. stock"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column != 5 || ids.get(row) == null;
        }
    };
    private final List<Long> ids = new ArrayList<>();
    private final JTable variantTable = new JTable(variantModel);

    private final CardLayout modeCards = new CardLayout();
    private final JPanel modePanel = new JPanel(modeCards);
    private final JLabel imageLabel = new JLabel();
    private byte[] imageBytes;
    private boolean imageChanged;

    public ProductDialog(Component parent, AppContext ctx, Long productId, String prefillBarcode, String prefillName) {
        super(parent, productId == null ? "Add product" : "Edit product", "Save product");
        this.ctx = ctx;
        this.existing = productId == null ? null : ctx.inventory().product(productId);
        setSubtitle(existing == null ? "Fields marked * are required. Scan the barcode straight into the barcode box."
                : "Stock is changed with \"Adjust stock\" or a purchase so every change is recorded.");
        buildForm();
        fill(prefillBarcode, prefillName);
    }

    public long savedId() {
        return savedId;
    }

    private void buildForm() {
        loadChoices();
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(description);
        descScroll.setPreferredSize(new Dimension(100, 52));
        JButton newCategory = Ui.icon("plus", "New category");
        newCategory.addActionListener(e -> addCategory());
        JPanel catRow = new JPanel(new BorderLayout(4, 0));
        catRow.setOpaque(false);
        catRow.add(category, BorderLayout.CENTER);
        catRow.add(newCategory, BorderLayout.EAST);

        JPanel basics = Ui.grid(2, 14, 10, Ui.row("Product name *", name), Ui.row("Category", catRow),
                Ui.row("Supplier", supplier), Ui.row("Description", descScroll));

        JPanel weightRow = new JPanel(new BorderLayout(4, 0));
        weightRow.setOpaque(false);
        weightRow.add(weight, BorderLayout.CENTER);
        weightRow.add(weightUnit, BorderLayout.EAST);
        unit.addActionListener(e -> unitChanged());
        JPanel selling = Ui.grid(2, 14, 10,
                Ui.row("Sold by (unit) *", unit, "Choose kg / g / L to sell by weight or volume"),
                Ui.row("Net weight / size of one item", weightRow, "e.g. 500 g bag (for information)"));

        // simple panel
        barcode.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("barcode", 16, Theme.MUTED));
        barcode.addActionListener(e -> {
            checkBarcode();
            sku.requestFocusInWindow(); // a scanner's Enter must not save the form
        });
        barcode.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                checkBarcode();
            }
        });
        barcodeStatus.setFont(Theme.font(11));
        Ui.onChange(cost, this::updateMargin);
        Ui.onChange(price, this::updateMargin);
        JPanel barcodeCell = new JPanel(new BorderLayout(0, 3));
        barcodeCell.setOpaque(false);
        barcodeCell.add(Ui.row("Barcode", barcode), BorderLayout.CENTER);
        barcodeCell.add(barcodeStatus, BorderLayout.SOUTH);
        JPanel priceCell = new JPanel(new BorderLayout(0, 3));
        priceCell.setOpaque(false);
        priceCell.add(Ui.row("Selling price *", price), BorderLayout.CENTER);
        priceCell.add(priceUnit, BorderLayout.SOUTH);
        JPanel costCell = new JPanel(new BorderLayout(0, 3));
        costCell.setOpaque(false);
        costCell.add(Ui.row("Purchase (cost) price", cost), BorderLayout.CENTER);
        costCell.add(margin, BorderLayout.SOUTH);
        JPanel simple = Ui.grid(2, 14, 10, barcodeCell, Ui.row("SKU / item code", sku), costCell, priceCell,
                Ui.row(existing == null ? "Opening stock" : "Current stock", stock),
                Ui.row("Low-stock alert at", minStock, "Warn when stock falls to this level"));

        // variation panel
        variantTable.setRowHeight(32);
        variantTable.putClientProperty("terminateEditOnFocusLost", true);
        variantTable.getTableHeader().setReorderingAllowed(false);
        variantTable.setSurrendersFocusOnKeystroke(true);
        JScrollPane vs = new JScrollPane(variantTable);
        vs.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        vs.setPreferredSize(new Dimension(600, 190));
        JButton addRow = Ui.secondary("Add variation", "plus");
        addRow.addActionListener(e -> addVariantRow(""));
        JButton removeRow = Ui.ghost("Remove selected", "trash");
        removeRow.addActionListener(e -> removeVariantRow());
        JButton sizes = Ui.ghost("Add S / M / L / XL", null);
        sizes.addActionListener(e -> {
            for (String s : new String[] {"Small", "Medium", "Large", "Extra Large"}) {
                addVariantRow(s);
            }
        });
        JPanel variantPanel = new JPanel(new BorderLayout(0, 8));
        variantPanel.setOpaque(false);
        variantPanel.add(Ui.muted("Each variation has its own barcode, prices and stock. Click a cell to type; "
                + "scan straight into the Barcode column."), BorderLayout.NORTH);
        variantPanel.add(vs, BorderLayout.CENTER);
        variantPanel.add(Ui.flowLeft(6, addRow, sizes, removeRow), BorderLayout.SOUTH);

        modePanel.setOpaque(false);
        modePanel.add(simple, "simple");
        modePanel.add(variantPanel, "variants");
        hasVariants.setOpaque(false);
        hasVariants.addActionListener(e -> switchMode());

        // image
        imageLabel.setPreferredSize(new Dimension(88, 88));
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JButton chooseImage = Ui.secondary("Choose picture…", null);
        chooseImage.addActionListener(e -> chooseImage());
        JButton removeImage = Ui.ghost("Remove", null);
        removeImage.addActionListener(e -> {
            imageBytes = null;
            imageChanged = true;
            showImage();
        });
        JPanel imagePanel = new JPanel(new BorderLayout(12, 0));
        imagePanel.setOpaque(false);
        imagePanel.add(imageLabel, BorderLayout.WEST);
        imagePanel.add(Ui.vbox(6, Ui.label("Picture (optional)"), chooseImage, removeImage), BorderLayout.CENTER);

        JPanel body = Ui.vbox(14, section("Product"), basics, section("How it is sold"), selling, hasVariants,
                modePanel, section("Picture"), imagePanel);
        setBody(body);
    }

    private static JComponent section(String title) {
        JLabel l = Ui.heading(title);
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        return l;
    }

    private void loadChoices() {
        Object selectedCat = category.getSelectedItem();
        category.removeAllItems();
        category.addItem("No category");
        for (Category c : ctx.inventory().categories()) {
            category.addItem(c);
        }
        if (selectedCat != null) {
            category.setSelectedItem(selectedCat);
        }
        if (supplier.getItemCount() == 0) {
            supplier.addItem("No supplier");
            for (Supplier s : ctx.parties().suppliers(false)) {
                supplier.addItem(s);
            }
        }
        if (unit.getItemCount() == 0) {
            for (Unit u : ctx.inventory().units(true)) {
                unit.addItem(u);
            }
        }
    }

    private void fill(String prefillBarcode, String prefillName) {
        if (existing == null) {
            minStock.setText(String.valueOf(ctx.settings().get().lowStockDefault()));
            barcode.setText(prefillBarcode == null ? "" : prefillBarcode);
            name.setText(prefillName == null ? "" : prefillName);
            unitChanged();
            switchMode();
            showImage();
            return;
        }
        name.setText(existing.name());
        description.setText(Text.trim(existing.description()));
        selectById(category, existing.categoryId());
        selectById(supplier, existing.supplierId());
        for (int i = 0; i < unit.getItemCount(); i++) {
            if (unit.getItemAt(i).id() == existing.unitId()) {
                unit.setSelectedIndex(i);
            }
        }
        if (unit.getSelectedItem() == null || ((Unit) unit.getSelectedItem()).id() != existing.unitId()) {
            Unit u = ctx.inventory().unit(existing.unitId());
            unit.addItem(u);
            unit.setSelectedItem(u);
        }
        weight.setText(Text.trim(existing.weightValue()));
        if (existing.weightUnit() != null) {
            weightUnit.setSelectedItem(existing.weightUnit());
        }
        hasVariants.setSelected(existing.hasVariants());
        List<Variant> active = existing.activeVariants();
        if (!existing.hasVariants() && !active.isEmpty()) {
            Variant v = active.get(0);
            ids.add(v.id());
            barcode.setText(Text.trim(v.barcode()));
            sku.setText(Text.trim(v.sku()));
            cost.setText(Money.plain(v.costPrice()));
            price.setText(Money.plain(v.salePrice()));
            stock.setText(Qty.format(v.stockQty()));
            minStock.setText(Qty.format(v.minStock()));
        } else {
            for (Variant v : active) {
                ids.add(v.id());
                variantModel.addRow(new Object[] {v.name(), Text.trim(v.barcode()), Text.trim(v.sku()),
                    Money.plain(v.costPrice()), Money.plain(v.salePrice()), Qty.format(v.stockQty()), Qty.format(v.minStock())});
            }
        }
        stock.setEditable(false);
        stock.setToolTipText("Use Inventory > Adjust stock or record a purchase to change stock.");
        imageBytes = existing.hasImage() ? ctx.inventory().image(existing.id()) : null;
        unitChanged();
        switchMode();
        showImage();
    }

    private static void selectById(JComboBox<Object> combo, Long id) {
        if (id == null) {
            combo.setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object o = combo.getItemAt(i);
            if ((o instanceof Category c && c.id() == id) || (o instanceof Supplier s && s.id() == id)) {
                combo.setSelectedIndex(i);
            }
        }
    }

    private void switchMode() {
        boolean v = hasVariants.isSelected();
        if (v && variantModel.getRowCount() == 0) {
            // carry over what was typed for the simple product
            Long firstId = ids.isEmpty() ? null : ids.get(0);
            ids.clear();
            ids.add(firstId);
            variantModel.addRow(new Object[] {"", barcode.getText().trim(), sku.getText().trim(), cost.getText().trim(),
                price.getText().trim(), stock.getText().trim().isEmpty() ? "0" : stock.getText().trim(),
                minStock.getText().trim()});
        }
        if (!v && variantModel.getRowCount() > 1) {
            if (!Msg.confirm(this, "Remove variations", "Turning off variations keeps only the first one. "
                    + "Variations with stock or sales are archived, not deleted.", "Keep first only")) {
                hasVariants.setSelected(true);
                return;
            }
        }
        if (!v && variantModel.getRowCount() >= 1) {
            barcode.setText(cell(0, 1));
            sku.setText(cell(0, 2));
            cost.setText(cell(0, 3));
            price.setText(cell(0, 4));
            stock.setText(cell(0, 5));
            minStock.setText(cell(0, 6));
            Long first = ids.get(0);
            ids.clear();
            ids.add(first);
            variantModel.setRowCount(0);
        }
        modeCards.show(modePanel, v ? "variants" : "simple");
    }

    private String cell(int row, int col) {
        Object o = variantModel.getValueAt(row, col);
        return o == null ? "" : o.toString().trim();
    }

    private void addVariantRow(String vname) {
        if (variantTable.isEditing()) {
            variantTable.getCellEditor().stopCellEditing();
        }
        String c = variantModel.getRowCount() > 0 ? cell(variantModel.getRowCount() - 1, 3) : cost.getText().trim();
        String p = variantModel.getRowCount() > 0 ? cell(variantModel.getRowCount() - 1, 4) : price.getText().trim();
        ids.add(null);
        variantModel.addRow(new Object[] {vname, "", "", c, p, "0", minStock.getText().trim()});
    }

    private void removeVariantRow() {
        int r = variantTable.getSelectedRow();
        if (r < 0) {
            return;
        }
        if (variantTable.isEditing()) {
            variantTable.getCellEditor().cancelCellEditing();
        }
        if (ids.get(r) != null && !Msg.confirm(this, "Remove variation", "Remove \"" + cell(r, 0) + "\"? If it has stock "
                + "or sales history it will be archived instead of deleted.", "Remove")) {
            return;
        }
        ids.remove(r);
        variantModel.removeRow(r);
    }

    private void unitChanged() {
        Unit u = (Unit) unit.getSelectedItem();
        if (u == null) {
            return;
        }
        priceUnit.setText(u.fractional() ? "Price for 1 " + u.code() + ". The till asks for the weight / amount." : " ");
        updateMargin();
    }

    private void updateMargin() {
        try {
            long c = Money.parse(cost.getText());
            long p = Money.parse(price.getText());
            if (p <= 0 || c <= 0) {
                margin.setText(" ");
                return;
            }
            long profit = p - c;
            margin.setForeground(profit < 0 ? Theme.BAD : Theme.GOOD);
            margin.setText((profit < 0 ? "Loss " : "Profit ") + Money.format(Math.abs(profit)) + " per item ("
                    + Math.round(profit * 1000.0 / p) / 10.0 + "%)");
        } catch (IllegalArgumentException e) {
            margin.setText(" ");
        }
    }

    private void checkBarcode() {
        String code = barcode.getText().trim();
        if (code.isEmpty()) {
            barcodeStatus.setText(" ");
            Ui.setError(barcode, false);
            return;
        }
        long exclude = ids.isEmpty() || ids.get(0) == null ? -1 : ids.get(0);
        String owner = ctx.inventory().barcodeOwner(code, exclude);
        if (owner != null) {
            barcodeStatus.setForeground(Theme.BAD);
            barcodeStatus.setText("⚠ Already used by " + owner);
            Ui.setError(barcode, true);
        } else {
            barcodeStatus.setForeground(Theme.GOOD);
            barcodeStatus.setText("✓ Barcode is free");
            Ui.setError(barcode, false);
        }
    }

    private void addCategory() {
        String n = Msg.ask(this, "New category", "Category name:", "");
        if (n == null) {
            return;
        }
        try {
            long id = ctx.inventory().addCategory(n);
            loadChoices();
            selectById(category, id);
        } catch (RuntimeException e) {
            Msg.error(this, e);
        }
    }

    private void chooseImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Pictures (JPG, PNG, GIF, BMP)", "jpg", "jpeg", "png", "gif", "bmp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            BufferedImage src = ImageIO.read(fc.getSelectedFile());
            if (src == null) {
                throw new IOException("This file is not a supported picture.");
            }
            imageBytes = thumbnail(src, 320);
            imageChanged = true;
            showImage();
        } catch (IOException e) {
            Msg.error(this, "The picture could not be loaded: " + e.getMessage());
        }
    }

    /** Scale to fit {@code max} px and encode as JPEG (stored inside the database so backups include it). */
    static byte[] thumbnail(BufferedImage src, int max) throws IOException {
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

    private void showImage() {
        if (imageBytes == null) {
            imageLabel.setIcon(Icons.get("inventory", 36, Theme.BORDER_STRONG));
            return;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            double s = Math.min(86.0 / img.getWidth(), 86.0 / img.getHeight());
            imageLabel.setIcon(new ImageIcon(img.getScaledInstance((int) (img.getWidth() * s),
                    (int) (img.getHeight() * s), java.awt.Image.SCALE_SMOOTH)));
        } catch (IOException | RuntimeException e) {
            imageLabel.setIcon(Icons.get("warning", 30, Theme.BAD));
        }
    }

    // ------------------------------------------------------------ save

    @Override
    protected boolean onSave() {
        if (variantTable.isEditing()) {
            variantTable.getCellEditor().stopCellEditing();
        }
        Unit u = (Unit) unit.getSelectedItem();
        if (u == null) {
            throw new com.smartbillingpro.db.AppException("Please choose a unit.");
        }
        if (name.getText().isBlank()) {
            Ui.setError(name, true);
            name.requestFocusInWindow();
            throw new com.smartbillingpro.db.AppException("Please enter the product name.");
        }
        List<InventoryService.VariantForm> variants = new ArrayList<>();
        if (!hasVariants.isSelected()) {
            long p = Ui.moneyValue(price, "the selling price", true);
            long c = Ui.moneyValue(cost, "the cost price", false);
            long open = existing == null ? Ui.qtyValue(stock, "opening stock", false) : 0;
            long min = Ui.qtyValue(minStock, "low-stock alert", false);
            if (c > p && p > 0 && !Msg.confirm(this, "Cost is higher than price",
                    "The cost price is higher than the selling price, so each sale makes a loss. Save anyway?", "Save anyway")) {
                return false;
            }
            variants.add(new InventoryService.VariantForm(ids.isEmpty() ? null : ids.get(0), "", barcode.getText(),
                    sku.getText(), c, p, open, min, true));
        } else {
            if (variantModel.getRowCount() == 0) {
                throw new com.smartbillingpro.db.AppException("Add at least one variation.");
            }
            for (int r = 0; r < variantModel.getRowCount(); r++) {
                String label = "row " + (r + 1);
                try {
                    long c = cell(r, 3).isEmpty() ? 0 : Money.parse(cell(r, 3));
                    long p = Money.parse(cell(r, 4));
                    long open = ids.get(r) == null && !cell(r, 5).isEmpty() ? Qty.parse(cell(r, 5)) : 0;
                    long min = cell(r, 6).isEmpty() ? 0 : Qty.parse(cell(r, 6));
                    if (cell(r, 4).isEmpty()) {
                        throw new IllegalArgumentException("enter a selling price");
                    }
                    variants.add(new InventoryService.VariantForm(ids.get(r), cell(r, 0), cell(r, 1), cell(r, 2), c, p,
                            open, min, true));
                } catch (IllegalArgumentException e) {
                    variantTable.setRowSelectionInterval(r, r);
                    throw new com.smartbillingpro.db.AppException("Variation " + label + ": " + e.getMessage());
                }
            }
        }
        Object cat = category.getSelectedItem();
        Object sup = supplier.getSelectedItem();
        InventoryService.ProductForm form = new InventoryService.ProductForm(existing == null ? null : existing.id(),
                name.getText(), cat instanceof Category c ? c.id() : null, description.getText(), u.id(),
                weight.getText(), weight.getText().isBlank() ? null : (String) weightUnit.getSelectedItem(),
                sup instanceof Supplier s ? s.id() : null, hasVariants.isSelected(), variants);
        savedId = ctx.inventory().saveProduct(form);
        if (imageChanged) {
            ctx.inventory().setImage(savedId, imageBytes);
        }
        return true;
    }
}
