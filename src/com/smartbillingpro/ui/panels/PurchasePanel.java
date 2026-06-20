package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.PurchaseOrder;
import com.smartbillingpro.model.PurchaseOrderItem;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.dialogs.PurchaseOrderDialog;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Receive stock from suppliers and review past purchase orders. The top table
 * lists every purchase order; selecting one shows the exact items that were
 * purchased in the lower table. Each order can be edited or deleted, and
 * deleting reverses the stock that was added.
 */
public class PurchasePanel extends PanelBase {

    private final DefaultTableModel orderModel = new DefaultTableModel(
            new Object[] {"Reference", "Date", "Supplier", "Items", "Total", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable orderTable = new StripedTable(orderModel);
    private final List<PurchaseOrder> orderRows = new ArrayList<>();

    private final DefaultTableModel itemModel = new DefaultTableModel(
            new Object[] {"Product", "Quantity", "Unit Cost", "Line Total"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable itemTable = new StripedTable(itemModel);
    private final javax.swing.JLabel itemHint = new javax.swing.JLabel();

    public PurchasePanel(MainFrame frame, AppContext context) {
        super(frame, context, "Purchases", "Receive stock and track purchase orders");

        RoundedButton receive = new RoundedButton("+ Receive Stock", Theme.PRIMARY, Color.WHITE);
        receive.addActionListener(e -> openReceiveDialog());
        addAction(receive);

        // --- orders (master) ---
        RoundedPanel orderCard = new RoundedPanel(new BorderLayout(0, 8));
        orderCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        orderCard.add(Ui.title("Purchase Orders", 14), BorderLayout.NORTH);
        orderCard.add(Ui.scroll(orderTable), BorderLayout.CENTER);
        JPanel orderBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        orderBar.setOpaque(false);
        RoundedButton edit = new RoundedButton("Edit", Theme.withAlpha(Theme.PRIMARY, 30), Theme.PRIMARY);
        edit.addActionListener(e -> editSelected());
        RoundedButton delete = new RoundedButton("Delete", Theme.withAlpha(Theme.DANGER, 30), Theme.DANGER);
        delete.addActionListener(e -> deleteSelected());
        orderBar.add(edit);
        orderBar.add(delete);
        orderCard.add(orderBar, BorderLayout.SOUTH);
        orderTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    showItems();
                }
            }
        });

        // --- purchased items (detail) ---
        RoundedPanel itemCard = new RoundedPanel(new BorderLayout(0, 8));
        itemCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        itemCard.add(Ui.title("Purchased Items", 14), BorderLayout.NORTH);
        itemCard.add(Ui.scroll(itemTable), BorderLayout.CENTER);
        itemHint.setFont(Theme.regular(12));
        itemHint.setForeground(Theme.MUTED);
        itemCard.add(itemHint, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, orderCard, itemCard);
        split.setResizeWeight(0.6);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        split.setDividerSize(10);

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(split, BorderLayout.CENTER);
        setBody(container);
        reload();
    }

    private PurchaseOrder selected() {
        int row = orderTable.getSelectedRow();
        if (row < 0 || row >= orderRows.size()) {
            return null;
        }
        return orderRows.get(row);
    }

    private void openReceiveDialog() {
        PurchaseOrderDialog dialog = new PurchaseOrderDialog(frame, context);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            reload();
            frame.refreshCurrent();
        }
    }

    private void editSelected() {
        PurchaseOrder order = selected();
        if (order == null) {
            JOptionPane.showMessageDialog(this, "Select a purchase order to edit.");
            return;
        }
        PurchaseOrderDialog dialog = new PurchaseOrderDialog(frame, context, order);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            reload();
            frame.refreshCurrent();
        }
    }

    private void deleteSelected() {
        PurchaseOrder order = selected();
        if (order == null) {
            JOptionPane.showMessageDialog(this, "Select a purchase order to delete.");
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete purchase order " + order.getReference() + "?\n"
                        + "The stock added by this order will be removed from inventory.",
                "Delete Purchase Order", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            context.getPurchaseService().delete(order.getId());
            reload();
            frame.refreshCurrent();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Could not delete: " + ex.getMessage());
        }
    }

    private void showItems() {
        itemModel.setRowCount(0);
        PurchaseOrder order = selected();
        if (order == null) {
            itemHint.setText("Select a purchase order above to see what was purchased.");
            return;
        }
        String symbol = symbol();
        List<PurchaseOrderItem> items = order.getItems();
        for (PurchaseOrderItem item : items) {
            itemModel.addRow(new Object[] {item.getProductName(), item.getQuantity(),
                    Money.format(symbol, item.getCostPrice()), Money.format(symbol, item.getLineTotal())});
        }
        itemHint.setText(items.size() + " item(s) in " + order.getReference()
                + "  \u2022  Total " + Money.format(symbol, order.getTotal()));
    }

    private void reload() {
        List<PurchaseOrder> orders = context.getPurchaseService().all();
        String symbol = symbol();
        orderRows.clear();
        orderModel.setRowCount(0);
        for (PurchaseOrder order : orders) {
            orderRows.add(order);
            orderModel.addRow(new Object[] {order.getReference(), order.getDate(), order.getSupplierName(),
                    order.getItemCount(), Money.format(symbol, order.getTotal()), order.getStatus()});
        }
        setSubtitle(orders.size() + " purchase order(s)");
        showItems();
    }

    @Override
    public void refreshData() {
        reload();
    }
}
