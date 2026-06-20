package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.SupplierDialog;
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

/** Manage suppliers / vendors used for purchase orders. */
public class SupplierPanel extends PanelBase {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Name", "Phone", "Address", "Notes"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable table = new StripedTable(model);
    private final List<Supplier> rows = new ArrayList<>();

    public SupplierPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Suppliers", "Vendors you buy stock from");

        RoundedButton add = new RoundedButton("+ Add Supplier", Theme.PRIMARY, Color.WHITE);
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

    private Supplier selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= rows.size()) {
            return null;
        }
        return rows.get(row);
    }

    private void openDialog(Supplier supplier) {
        if (new SupplierDialog(frame, context, supplier).showDialog()) {
            reload();
        }
    }

    private void editSelected() {
        Supplier supplier = selected();
        if (supplier == null) {
            JOptionPane.showMessageDialog(this, "Select a supplier to edit.");
            return;
        }
        openDialog(supplier);
    }

    private void deleteSelected() {
        Supplier supplier = selected();
        if (supplier == null) {
            JOptionPane.showMessageDialog(this, "Select a supplier to delete.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete \"" + supplier.getName() + "\"?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            context.getSupplierService().delete(supplier);
            reload();
        }
    }

    private void reload() {
        List<Supplier> suppliers = context.getSupplierService().all();
        rows.clear();
        model.setRowCount(0);
        for (Supplier supplier : suppliers) {
            rows.add(supplier);
            model.addRow(new Object[] {supplier.getName(), supplier.getPhone(),
                    supplier.getAddress(), supplier.getNotes()});
        }
        setSubtitle(suppliers.size() + " supplier(s)");
    }

    @Override
    public void refreshData() {
        reload();
    }
}
