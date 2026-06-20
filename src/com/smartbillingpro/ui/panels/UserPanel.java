package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Store;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.AdminFrame;
import com.smartbillingpro.ui.Refreshable;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StatusCellRenderer;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
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
            new Object[] {"Username", "Full Name", "Role", "Store", "Status"}, 0) {
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

        table.getColumnModel().getColumn(4).setCellRenderer(new StatusCellRenderer());
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

    private void reload() {
        // Build a quick id -> name lookup so we can show each user's store.
        Map<Integer, String> storeNames = new HashMap<>();
        for (Store store : context.getStoreService().all()) {
            storeNames.put(store.getId(), store.getName());
        }
        List<User> users = context.getAuthService().listUsers();
        rows.clear();
        model.setRowCount(0);
        for (User user : users) {
            rows.add(user);
            String storeLabel = user.getStoreId() == 0
                    ? "\u2014"
                    : storeNames.getOrDefault(user.getStoreId(), "(store #" + user.getStoreId() + ")");
            model.addRow(new Object[] {user.getUsername(), user.getFullName(),
                    user.getRole().getLabel(), storeLabel, user.isActive() ? "Active" : "Disabled"});
        }
        subtitle.setText(users.size() + " user account(s)");
    }

    @Override
    public void refreshData() {
        reload();
    }
}
