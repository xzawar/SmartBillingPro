package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.CloudAccess;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.StatusCellRenderer;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.InventoryJson;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin-only drill-down into a store owner's account. Shows every account the
 * owner has created (which the admin can enable or disable), the store's full
 * inventory, and a button to export that inventory as a JSON file that can be
 * imported into a brand-new account.
 */
public class StoreDetailsDialog extends JDialog {

    private final AppContext context;
    private final int storeId;

    private final DefaultTableModel staffModel = new DefaultTableModel(
            new Object[] {"Username", "Full Name", "Role", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable staffTable = new StripedTable(staffModel);
    private final List<User> staffRows = new ArrayList<>();

    private final DefaultTableModel invModel = new DefaultTableModel(
            new Object[] {"Product", "SKU", "Category", "Cost", "Sale", "Qty"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable invTable = new StripedTable(invModel);
    private final JLabel invSummary = new JLabel();

    public StoreDetailsDialog(Window owner, AppContext context, User ownerUser) {
        super(owner, "Store Details", ModalityType.APPLICATION_MODAL);
        this.context = context;
        this.storeId = ownerUser.getStoreId();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);

        GradientPanel header = new GradientPanel(new BorderLayout());
        header.setColors(Theme.PRIMARY, Theme.ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 22));
        JLabel heading = new JLabel("Store managed by " + ownerUser.getDisplayName());
        heading.setFont(Theme.bold(18));
        heading.setForeground(Color.WHITE);
        header.add(heading, BorderLayout.WEST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Accounts", buildStaffTab());
        tabs.addTab("Inventory", buildInventoryTab());

        root.add(header, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
        setSize(new Dimension(780, 560));
        setLocationRelativeTo(owner);
        reloadStaff();
        reloadInventory();
    }

    private JPanel buildStaffTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Theme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        staffTable.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());
        panel.add(Ui.scroll(staffTable), BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        bar.setOpaque(false);
        RoundedButton toggle = new RoundedButton("Enable / Disable Account", Theme.PRIMARY, Color.WHITE);
        toggle.setArc(26);
        toggle.addActionListener(e -> toggleStaff());
        bar.add(toggle);
        RoundedButton delete = new RoundedButton("Delete Account", Theme.DANGER, Color.WHITE);
        delete.setArc(26);
        delete.addActionListener(e -> deleteStaff());
        bar.add(delete);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildInventoryTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Theme.BG);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        panel.add(Ui.scroll(invTable), BorderLayout.CENTER);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        invSummary.setFont(Theme.regular(13));
        invSummary.setForeground(Theme.MUTED);
        bar.add(invSummary, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        buttons.setOpaque(false);
        RoundedButton export = new RoundedButton("Export Inventory (JSON)", Theme.SUCCESS, Color.WHITE);
        export.setArc(26);
        export.addActionListener(e -> exportInventory());
        buttons.add(export);
        bar.add(buttons, BorderLayout.EAST);
        panel.add(bar, BorderLayout.SOUTH);
        return panel;
    }

    private void toggleStaff() {
        int row = staffTable.getSelectedRow();
        if (row < 0 || row >= staffRows.size()) {
            JOptionPane.showMessageDialog(this, "Select an account to enable or disable.");
            return;
        }
        User user = staffRows.get(row);
        user.setActive(!user.isActive());
        context.getAuthService().updateUser(user);
        reloadStaff();
    }

    /** Move the selected staff account to the cloud Recycle Bin. */
    private void deleteStaff() {
        int row = staffTable.getSelectedRow();
        if (row < 0 || row >= staffRows.size()) {
            JOptionPane.showMessageDialog(this, "Select an account to delete.");
            return;
        }
        User user = staffRows.get(row);
        if (!CloudAccess.isConfigured()) {
            JOptionPane.showMessageDialog(this,
                    "Account deletion runs through the cloud, which is not configured on this device.");
            return;
        }
        String remoteUuid = context.getAuthService().remoteUuid(user.getId());
        if (remoteUuid == null) {
            JOptionPane.showMessageDialog(this,
                    "This account has not synced to the cloud yet, so it cannot be deleted online.");
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Move \"" + user.getUsername() + "\" to the Recycle Bin?\n"
                        + "You have 24 hours to restore it from the Deleted screen.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            CloudAccess.accountAction(context, "delete", remoteUuid, null);
            reloadStaff();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not delete the account: " + ex.getMessage());
        }
    }

    private void reloadStaff() {
        staffRows.clear();
        staffModel.setRowCount(0);
        for (User user : context.getAuthService().listUsersForStore(storeId)) {
            // The built-in administrator is global, not part of any store's
            // staff, so it should never appear in an owner's account list.
            if (user.getRole() == Role.ADMIN) {
                continue;
            }
            staffRows.add(user);
            staffModel.addRow(new Object[] {user.getUsername(), user.getFullName(),
                    user.getRole().getLabel(), user.isActive() ? "Active" : "Disabled"});
        }
    }

    private void reloadInventory() {
        invModel.setRowCount(0);
        String symbol = context.getSettings().getCurrencySymbol();
        List<Product> products = context.getInventoryService().allForStore(storeId);
        for (Product product : products) {
            invModel.addRow(new Object[] {product.getName(), product.getSku(), product.getCategory(),
                    Money.format(symbol, product.getCostPrice()), Money.format(symbol, product.getSalePrice()),
                    product.getQuantity()});
        }
        invSummary.setText(products.size() + " product(s) in this store's inventory");
    }

    private void exportInventory() {
        List<Product> products = context.getInventoryService().allForStore(storeId);
        if (products.isEmpty()) {
            JOptionPane.showMessageDialog(this, "This store has no inventory to export yet.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export inventory as JSON");
        chooser.setSelectedFile(new File("inventory-store-" + storeId + ".json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            File file = chooser.getSelectedFile();
            Files.write(file.toPath(), InventoryJson.export(products).getBytes());
            JOptionPane.showMessageDialog(this, "Exported " + products.size()
                    + " product(s) to:\n" + file.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not export the file: " + ex.getMessage());
        }
    }
}
