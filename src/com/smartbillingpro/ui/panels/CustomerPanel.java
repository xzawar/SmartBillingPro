package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Customer;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.CustomerDialog;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Manage customers and view their loyalty points. */
public class CustomerPanel extends PanelBase {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Name", "Phone", "Address", "Loyalty Points"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable table = new StripedTable(model);
    private final JTextField searchField = new JTextField(16);
    private final List<Customer> rows = new ArrayList<>();

    public CustomerPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Customers", "Your customer directory");

        Ui.styleField(searchField);
        searchField.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                reload();
            }
        });
        addAction(Ui.formLabel("Search  "));
        addAction(searchField);
        RoundedButton add = new RoundedButton("+ Add Customer", Theme.PRIMARY, Color.WHITE);
        add.addActionListener(e -> openDialog(null));
        addAction(add);

        RoundedPanel tableCard = new RoundedPanel(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tableCard.add(Ui.scroll(table), BorderLayout.CENTER);

        JPanel container = new JPanel(new BorderLayout(0, 10));
        container.setOpaque(false);
        container.add(tableCard, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        RoundedButton edit = new RoundedButton("Edit", Theme.withAlpha(Theme.PRIMARY, 30), Theme.PRIMARY);
        edit.addActionListener(e -> editSelected());
        RoundedButton delete = new RoundedButton("Delete", Theme.withAlpha(Theme.DANGER, 30), Theme.DANGER);
        delete.addActionListener(e -> deleteSelected());
        bar.add(edit);
        bar.add(delete);
        container.add(bar, BorderLayout.SOUTH);
        setBody(container);
    }

    private Customer selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        return rows.get(row);
    }

    private void openDialog(Customer customer) {
        if (new CustomerDialog(frame, context, customer).showDialog()) {
            reload();
        }
    }

    private void editSelected() {
        Customer customer = selected();
        if (customer == null) {
            JOptionPane.showMessageDialog(this, "Select a customer to edit.");
            return;
        }
        openDialog(customer);
    }

    private void deleteSelected() {
        Customer customer = selected();
        if (customer == null) {
            JOptionPane.showMessageDialog(this, "Select a customer to delete.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete \"" + customer.getName() + "\"?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            context.getCustomerService().delete(customer);
            reload();
        }
    }

    private void reload() {
        String term = searchField.getText().trim();
        List<Customer> customers = term.isEmpty()
                ? context.getCustomerService().all()
                : context.getCustomerService().search(term);
        rows.clear();
        model.setRowCount(0);
        for (Customer customer : customers) {
            rows.add(customer);
            model.addRow(new Object[] {customer.getName(), customer.getPhone(),
                    customer.getAddress(), customer.getLoyaltyPoints()});
        }
        setSubtitle(customers.size() + " customer(s)");
    }

    @Override
    public void refreshData() {
        reload();
    }
}
