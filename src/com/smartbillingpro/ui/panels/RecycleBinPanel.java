package com.smartbillingpro.ui.panels;

import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.CloudAccess;
import com.smartbillingpro.ui.AdminFrame;
import com.smartbillingpro.ui.Refreshable;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin Recycle Bin. Stores and accounts that are deleted from the desktop app
 * or the web console are kept here (soft-deleted) for 24 hours. The admin can
 * restore them, or remove them permanently right away. Anything older than 24
 * hours is purged automatically by the cloud.
 */
public class RecycleBinPanel extends JPanel implements Refreshable {

    private final AdminFrame frame;
    private final AppContext context;
    private final JLabel subtitle = new JLabel();

    private final DefaultTableModel storeModel = new DefaultTableModel(
            new Object[] {"Store", "Phone", "Deleted"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable storeTable = new StripedTable(storeModel);
    private final List<String> storeIds = new ArrayList<>();

    private final DefaultTableModel accountModel = new DefaultTableModel(
            new Object[] {"Username", "Full Name", "Role", "Deleted"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable accountTable = new StripedTable(accountModel);
    private final List<String> accountIds = new ArrayList<>();

    public RecycleBinPanel(AdminFrame frame, AppContext context) {
        this.frame = frame;
        this.context = context;
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);

        JPanel grid = new JPanel(new java.awt.GridLayout(2, 1, 0, 14));
        grid.setOpaque(false);
        grid.add(buildSection("Deleted stores", storeTable,
                () -> trash("restore", "store"), () -> trash("purge", "store")));
        grid.add(buildSection("Deleted accounts", accountTable,
                () -> trash("restore", "account"), () -> trash("purge", "account")));
        add(grid, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
        JLabel title = Ui.title("Recycle Bin", 22);
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setFont(Theme.regular(13));
        subtitle.setForeground(Theme.MUTED);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);
        return header;
    }

    private JPanel buildSection(String label, StripedTable table, Runnable onRestore, Runnable onPurge) {
        JPanel wrap = new JPanel(new BorderLayout(0, 8));
        wrap.setOpaque(false);
        JLabel heading = new JLabel(label);
        heading.setFont(Theme.bold(15));
        heading.setForeground(Theme.TEXT);
        wrap.add(heading, BorderLayout.NORTH);

        RoundedPanel card = new RoundedPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.add(Ui.scroll(table), BorderLayout.CENTER);
        wrap.add(card, BorderLayout.CENTER);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        RoundedButton restore = new RoundedButton("Restore", Theme.SUCCESS, Color.WHITE);
        restore.addActionListener(e -> onRestore.run());
        RoundedButton purge = new RoundedButton("Delete permanently", Theme.DANGER, Color.WHITE);
        purge.addActionListener(e -> onPurge.run());
        bar.add(restore);
        bar.add(purge);
        wrap.add(bar, BorderLayout.SOUTH);
        return wrap;
    }

    private void trash(String action, String kind) {
        boolean store = "store".equals(kind);
        StripedTable table = store ? storeTable : accountTable;
        List<String> ids = store ? storeIds : accountIds;
        int row = table.getSelectedRow();
        if (row < 0 || row >= ids.size()) {
            JOptionPane.showMessageDialog(this, "Select a " + kind + " first.");
            return;
        }
        if ("purge".equals(action) && JOptionPane.showConfirmDialog(this,
                "Delete this " + kind + " permanently? This cannot be undone.",
                "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            CloudAccess.trashAction(context, action, kind, ids.get(row));
            reload();
            frame.refreshCurrent();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not " + action + " the " + kind + ": " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void reload() {
        storeIds.clear();
        storeModel.setRowCount(0);
        accountIds.clear();
        accountModel.setRowCount(0);
        if (!CloudAccess.isConfigured()) {
            subtitle.setText("The cloud is not configured on this device.");
            return;
        }
        try {
            Map<String, Object> data = CloudAccess.listTrash(context);
            Object storesObj = data.get("stores");
            if (storesObj instanceof List) {
                for (Object o : (List<Object>) storesObj) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> s = (Map<String, Object>) o;
                    storeIds.add(str(s.get("id")));
                    storeModel.addRow(new Object[] {str(s.get("name")), safe(str(s.get("phone"))),
                            when(str(s.get("deleted_at")))});
                }
            }
            Object accountsObj = data.get("accounts");
            if (accountsObj instanceof List) {
                for (Object o : (List<Object>) accountsObj) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> a = (Map<String, Object>) o;
                    accountIds.add(str(a.get("id")));
                    accountModel.addRow(new Object[] {str(a.get("username")), safe(str(a.get("full_name"))),
                            str(a.get("role")), when(str(a.get("deleted_at")))});
                }
            }
            subtitle.setText(storeIds.size() + " store(s) and " + accountIds.size()
                    + " account(s) in the bin \u2022 auto-removed after 24 hours");
        } catch (Exception ex) {
            subtitle.setText("Could not load the Recycle Bin: " + ex.getMessage());
        }
    }

    private static String when(String iso) {
        if (iso == null || iso.length() < 16) {
            return iso == null ? "\u2014" : iso;
        }
        return iso.substring(0, 16).replace("T", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    @Override
    public void refreshData() {
        reload();
    }
}
