package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Product;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StatusCellRenderer;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.ProductDialog;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.InventoryJson;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Browse, search and maintain the product catalogue. */
public class InventoryPanel extends PanelBase {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Name", "SKU", "Category", "Cost", "Sale", "Qty", "Status", "Supplier"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable table = new StripedTable(model);
    private final JTextField searchField = new JTextField(16);
    private final List<Product> rows = new ArrayList<>();
    private final boolean canManage;

    public InventoryPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Inventory", "Your product catalogue and stock levels");
        this.canManage = context.getCurrentUser().getRole().canManageInventory();

        Ui.styleField(searchField);
        searchField.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                reload();
            }
        });
        addAction(Ui.formLabel("Search  "));
        addAction(searchField);
        if (canManage) {
            RoundedButton add = new RoundedButton("+ Add Product", Theme.PRIMARY, Color.WHITE);
            add.addActionListener(e -> openDialog(null));
            addAction(add);
        }

        table.getColumnModel().getColumn(6).setCellRenderer(new StatusCellRenderer());

        RoundedPanel tableCard = new RoundedPanel(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tableCard.add(Ui.scroll(table), BorderLayout.CENTER);

        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);
        container.add(tableCard, BorderLayout.CENTER);

        if (canManage) {
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            bar.setOpaque(false);
            RoundedButton edit = new RoundedButton("Edit", Theme.withAlpha(Theme.PRIMARY, 30), Theme.PRIMARY);
            edit.addActionListener(e -> editSelected());
            RoundedButton delete = new RoundedButton("Delete", Theme.withAlpha(Theme.DANGER, 30), Theme.DANGER);
            delete.addActionListener(e -> deleteSelected());
            // Import / export the whole catalogue as a JSON file so it can be
            // moved into a brand-new account.
            RoundedButton importBtn = new RoundedButton("Import JSON", Theme.withAlpha(Theme.ACCENT, 30), Theme.ACCENT);
            importBtn.addActionListener(e -> importInventory());
            RoundedButton exportBtn = new RoundedButton("Export JSON", Theme.withAlpha(Theme.SUCCESS, 30), Theme.SUCCESS);
            exportBtn.addActionListener(e -> exportInventory());
            bar.add(edit);
            bar.add(delete);
            bar.add(importBtn);
            bar.add(exportBtn);
            container.add(bar, BorderLayout.SOUTH);
        }
        setBody(container);
    }

    private Product selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        return rows.get(row);
    }

    private void openDialog(Product product) {
        if (new ProductDialog(frame, context, product).showDialog()) {
            reload();
        }
    }

    private void editSelected() {
        Product product = selected();
        if (product == null) {
            JOptionPane.showMessageDialog(this, "Select a product to edit.");
            return;
        }
        openDialog(product);
    }

    private void deleteSelected() {
        Product product = selected();
        if (product == null) {
            JOptionPane.showMessageDialog(this, "Select a product to delete.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete \"" + product.getName() + "\"?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            context.getInventoryService().delete(product);
            reload();
        }
    }

    /** Save the whole catalogue to a JSON file the user picks. */
    private void exportInventory() {
        List<Product> products = context.getInventoryService().all();
        if (products.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no products to export yet.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Inventory");
        chooser.setSelectedFile(new File("inventory.json"));
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getParentFile(), file.getName() + ".json");
        }
        try {
            Files.write(file.toPath(), InventoryJson.export(products).getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(this,
                    "Exported " + products.size() + " product(s) to:\n" + file.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save the file: " + ex.getMessage(),
                    "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Load products from a JSON file and add them to this store's catalogue. */
    private void importInventory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Inventory");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<Product> products = InventoryJson.parse(json);
            if (products.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No products were found in that file.");
                return;
            }
            int choice = JOptionPane.showConfirmDialog(this,
                    "Add " + products.size() + " product(s) from this file to your inventory?",
                    "Confirm Import", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            int added = context.getInventoryService().importProducts(products);
            reload();
            JOptionPane.showMessageDialog(this, "Imported " + added + " product(s).");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read the file: " + ex.getMessage(),
                    "Import Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reload() {
        InventoryService inventory = context.getInventoryService();
        String term = searchField.getText().trim();
        List<Product> products = term.isEmpty() ? inventory.all() : inventory.search(term);
        int threshold = settings().getLowStockThreshold();
        String symbol = symbol();
        rows.clear();
        model.setRowCount(0);
        for (Product product : products) {
            rows.add(product);
            model.addRow(new Object[] {
                    product.getName(), product.getSku(), product.getCategory(),
                    Money.format(symbol, product.getCostPrice()),
                    Money.format(symbol, product.getSalePrice()),
                    product.getQuantity(),
                    InventoryService.stockStatus(product, threshold),
                    product.getSupplierName()});
        }
        setSubtitle(products.size() + " product(s)");
    }

    @Override
    public void refreshData() {
        reload();
    }
}
