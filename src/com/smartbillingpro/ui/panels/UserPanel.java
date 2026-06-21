package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.Store;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.CloudAccess;
import com.smartbillingpro.ui.AdminFrame;
import com.smartbillingpro.ui.Refreshable;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StatusCellRenderer;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.StoreDetailsDialog;
import com.smartbillingpro.ui.dialogs.UserDialog;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin screen to manage every user account across all stores. The admin can
 * create any type of user (store owner, manager or cashier) and assign each one
 * to a store. This is the only place accounts are created.
 */
public class UserPanel extends JPanel implements Refreshable {

    private final AdminFrame frame;
    private final AppContext context;
    private final JLabel subtitle = new JLabel();

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Username", "Full Name", "Branch", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable table = new StripedTable(model);
    private final List<User> rows = new ArrayList<>();

    public UserPanel(AdminFrame frame, AppContext context) {
        this.frame = frame;
        this.context = context;
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);

        table.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());
        RoundedPanel tableCard = new RoundedPanel(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tableCard.add(Ui.scroll(table), BorderLayout.CENTER);

        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);
        container.add(tableCard, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        RoundedButton edit = new RoundedButton("Edit / Reset Password", Theme.withAlpha(Theme.PRIMARY, 30), Theme.PRIMARY);
        edit.addActionListener(e -> editSelected());
        bar.add(edit);
        // "Details" opens an owner's store: their staff, inventory and a JSON export.
        RoundedButton details = new RoundedButton("Details", Theme.withAlpha(Theme.ACCENT, 30), Theme.ACCENT);
        details.addActionListener(e -> openDetails());
        bar.add(details);
        RoundedButton delete = new RoundedButton("Delete", Theme.DANGER, Color.WHITE);
        delete.addActionListener(e -> deleteSelected());
        bar.add(delete);
        container.add(bar, BorderLayout.SOUTH);
        add(container, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = Ui.title("User Accounts", 22);
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setFont(Theme.regular(13));
        subtitle.setForeground(Theme.MUTED);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        RoundedButton add = new RoundedButton("+ New User", Theme.PRIMARY, Color.WHITE);
        add.addActionListener(e -> openDialog(null));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(add);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private User selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        return rows.get(row);
    }

    private void openDialog(User user) {
        if (new UserDialog(frame, context, user).showDialog()) {
            reload();
        }
    }

    private void editSelected() {
        User user = selected();
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Select a user to edit.");
            return;
        }
        openDialog(user);
    }

    /** Move the selected owner account to the cloud Recycle Bin. */
    private void deleteSelected() {
        User user = selected();
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Select an account to delete.");
            return;
        }
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
            reload();
            frame.refreshCurrent();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not delete the account: " + ex.getMessage());
        }
    }

    /** Drill into a store owner's account: their staff, inventory and export. */
    private void openDetails() {
        User user = selected();
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Select an owner account to view its details.");
            return;
        }
        if (user.getRole() != Role.OWNER) {
            JOptionPane.showMessageDialog(this,
                    "Details are available for store owner accounts only.\n"
                            + "Select an Owner to see the staff and inventory they manage.");
            return;
        }
        new StoreDetailsDialog(frame, context, user).setVisible(true);
        reload();
    }

    private void reload() {
        // Build a quick id -> name lookup so we can show each user's store.
        Map<Integer, String> storeNames = new HashMap<>();
        for (Store store : context.getStoreService().all()) {
            storeNames.put(store.getId(), store.getName());
        }
        List<User> users = context.getAuthService().listUsers();
        rows.clear();
        model.setRowCount(0);
        int owners = 0;
        for (User user : users) {
            // The admin console manages store OWNERS only. Each owner then
            // creates and manages their own managers and cashiers from inside
            // their store account, so those never appear on this screen.
            if (user.getRole() != Role.OWNER) {
                continue;
            }
            rows.add(user);
            owners++;
            String storeLabel = user.getStoreId() == 0
                    ? "\u2014"
                    : storeNames.getOrDefault(user.getStoreId(), "(store #" + user.getStoreId() + ")");
            model.addRow(new Object[] {user.getUsername(), user.getFullName(),
                    storeLabel, user.isActive() ? "Active" : "Disabled"});
        }
        subtitle.setText(owners + " store owner(s)");
    }

    @Override
    public void refreshData() {
        reload();
    }
}
