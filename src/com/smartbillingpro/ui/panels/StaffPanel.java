package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.CloudAccess;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StatusCellRenderer;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.StaffDialog;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * The store owner's screen for building their own team. Owners cannot sell, but
 * they create the managers and cashiers who do, and can enable or disable them
 * at any time. Everyone created here belongs to the owner's store.
 */
public class StaffPanel extends PanelBase {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Username", "Full Name", "Role", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable table = new StripedTable(model);
    private final List<User> rows = new ArrayList<>();

    public StaffPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Staff", "Create and manage the managers and cashiers in your store");

        RoundedButton add = new RoundedButton("+ Add Staff", Theme.PRIMARY, Color.WHITE);
        add.addActionListener(e -> openDialog(null));
        addAction(add);

        table.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());
        RoundedPanel card = new RoundedPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.add(Ui.scroll(table), BorderLayout.CENTER);

        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);
        container.add(card, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        RoundedButton edit = new RoundedButton("Edit / Reset Password", Theme.withAlpha(Theme.PRIMARY, 30), Theme.PRIMARY);
        edit.addActionListener(e -> editSelected());
        RoundedButton toggle = new RoundedButton("Enable / Disable", Theme.withAlpha(Theme.ACCENT, 30), Theme.ACCENT);
        toggle.addActionListener(e -> toggleSelected());
        RoundedButton delete = new RoundedButton("Delete", Theme.DANGER, Color.WHITE);
        delete.addActionListener(e -> deleteSelected());
        bar.add(edit);
        bar.add(toggle);
        bar.add(delete);
        container.add(bar, BorderLayout.SOUTH);
        setBody(container);
        reload();
    }

    private User selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        return rows.get(row);
    }

    private void openDialog(User user) {
        if (new StaffDialog(frame, context, user).showDialog()) {
            reload();
        }
    }

    private void editSelected() {
        User user = selected();
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Select a staff member to edit.");
            return;
        }
        openDialog(user);
    }

    private void toggleSelected() {
        User user = selected();
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Select a staff member first.");
            return;
        }
        user.setActive(!user.isActive());
        context.getAuthService().updateUser(user);
        reload();
    }

    /** Move the selected staff member to the cloud Recycle Bin. */
    private void deleteSelected() {
        User user = selected();
        if (user == null) {
            JOptionPane.showMessageDialog(this, "Select a staff member to delete.");
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
                        + "You have 24 hours to restore it before it is deleted for good.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            CloudAccess.accountAction(context, "delete", remoteUuid, null);
            reload();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not delete the staff member: " + ex.getMessage());
        }
    }

    private void reload() {
        rows.clear();
        model.setRowCount(0);
        for (User user : context.getAuthService().listUsersForStore(context.getCurrentStoreId())) {
            if (user.getRole() == Role.OWNER || user.getRole() == Role.ADMIN) {
                continue; // the owner manages staff but is not listed as their own staff;
                          // the global administrator is never part of a store's team
            }
            rows.add(user);
            model.addRow(new Object[] {user.getUsername(), user.getFullName(),
                    user.getRole().getLabel(), user.isActive() ? "Active" : "Disabled"});
        }
        setSubtitle(rows.size() + " staff member(s) in your store");
    }

    @Override
    public void refreshData() {
        reload();
    }
}
