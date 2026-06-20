package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Invoice;
import com.smartbillingpro.model.InvoiceStatus;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.StatCard;
import com.smartbillingpro.ui.components.StatusCellRenderer;
import com.smartbillingpro.ui.components.StripedTable;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Landing page: headline metrics, recent sales and stock alerts. */
public class DashboardPanel extends PanelBase {

    private final StatCard salesCard = new StatCard("Today's Sales", Theme.SUCCESS);
    private final StatCard ordersCard = new StatCard("Today's Orders", Theme.INFO);
    private final StatCard productsCard = new StatCard("Products", Theme.PRIMARY);
    private final StatCard alertCard = new StatCard("Stock Alerts", Theme.WARNING);

    private final DefaultTableModel recentModel =
            readOnlyModel("Invoice", "Date", "Total", "Status");
    private final DefaultTableModel alertModel =
            readOnlyModel("Product", "Qty", "Status");

    public DashboardPanel(MainFrame frame, AppContext context) {
        super(frame, context, "Dashboard", "Live overview of your store");

        JPanel cards = new JPanel(new GridLayout(1, 4, 16, 0));
        cards.setOpaque(false);
        cards.add(salesCard);
        cards.add(ordersCard);
        cards.add(productsCard);
        cards.add(alertCard);

        StripedTable recentTable = new StripedTable(recentModel);
        recentTable.getColumnModel().getColumn(3).setCellRenderer(new StatusCellRenderer());
        StripedTable alertTable = new StripedTable(alertModel);
        alertTable.getColumnModel().getColumn(2).setCellRenderer(new StatusCellRenderer());

        JPanel tables = new JPanel(new GridLayout(1, 2, 16, 0));
        tables.setOpaque(false);
        tables.add(titledCard("Recent Sales", recentTable));
        tables.add(titledCard("Low / Out of Stock", alertTable));

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(cards, BorderLayout.NORTH);
        body.add(tables, BorderLayout.CENTER);
        setBody(body);
    }

    private RoundedPanel titledCard(String title, JComponent table) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel heading = Ui.title(title, 15);
        card.add(heading, BorderLayout.NORTH);
        card.add(Ui.scroll(table), BorderLayout.CENTER);
        return card;
    }

    private DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    @Override
    public void refreshData() {
        InventoryService inventory = context.getInventoryService();
        int threshold = settings().getLowStockThreshold();
        String symbol = symbol();
        LocalDate today = LocalDate.now();

        BigDecimal todayTotal = BigDecimal.ZERO;
        int todayCount = 0;
        recentModel.setRowCount(0);
        int shown = 0;
        for (Invoice invoice : context.getInvoiceService().all()) {
            if (invoice.getStatus() == InvoiceStatus.COMPLETED
                    && today.equals(Dates.dateOf(invoice.getCreatedAt()))) {
                todayTotal = todayTotal.add(invoice.getGrandTotal());
                todayCount++;
            }
            if (shown < 8) {
                recentModel.addRow(new Object[] {invoice.getInvoiceNumber(), invoice.getCreatedAt(),
                        Money.format(symbol, invoice.getGrandTotal()), invoice.getStatus().getLabel()});
                shown++;
            }
        }

        salesCard.setValue(Money.format(symbol, todayTotal));
        salesCard.setHint(todayCount + " sale(s) today");
        ordersCard.setValue(String.valueOf(todayCount));
        ordersCard.setHint("Completed today");
        productsCard.setValue(String.valueOf(inventory.productCount()));
        productsCard.setHint(inventory.totalUnits() + " units in stock");

        List<Product> low = inventory.lowStock(threshold);
        List<Product> out = inventory.outOfStock();
        alertModel.setRowCount(0);
        for (Product product : out) {
            alertModel.addRow(new Object[] {product.getName(), product.getQuantity(), "Out of Stock"});
        }
        for (Product product : low) {
            alertModel.addRow(new Object[] {product.getName(), product.getQuantity(), "Low Stock"});
        }
        alertCard.setValue(String.valueOf(low.size() + out.size()));
        alertCard.setHint(out.size() + " out, " + low.size() + " low");
    }
}
