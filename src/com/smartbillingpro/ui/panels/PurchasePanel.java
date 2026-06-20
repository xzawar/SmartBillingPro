package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.PurchaseOrder;
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
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;

/** Receive stock from suppliers and review past purchase orders. */
public class PurchasePanel extends PanelBase {

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Reference", "Date", "Supplier", "Items", "Total", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final StripedTable table = new StripedTable(model);

    public PurchasePanel(MainFrame frame, AppContext context) {
        super(frame, context, "Purchases", "Receive stock and track purchase orders");

        RoundedButton receive = new RoundedButton("+ Receive Stock", Theme.PRIMARY, Color.WHITE);
        receive.addActionListener(e -> openDialog());
        addAction(receive);

        RoundedPanel tableCard = new RoundedPanel(new BorderLayout());
        tableCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tableCard.add(Ui.scroll(table), BorderLayout.CENTER);

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(tableCard, BorderLayout.CENTER);
        setBody(container);
    }

    private void openDialog() {
        PurchaseOrderDialog dialog = new PurchaseOrderDialog(frame, context);
        dialog.setVisible(true);
        if (dialog.isReceived()) {
            reload();
            frame.refreshCurrent();
        }
    }

    private void reload() {
        List<PurchaseOrder> orders = context.getPurchaseService().all();
        String symbol = symbol();
        model.setRowCount(0);
        for (PurchaseOrder order : orders) {
            model.addRow(new Object[] {order.getReference(), order.getDate(), order.getSupplierName(),
                    order.getItemCount(), Money.format(symbol, order.getTotal()), order.getStatus()});
        }
        setSubtitle(orders.size() + " purchase order(s)");
    }

    @Override
    public void refreshData() {
        reload();
    }
}
