package com.smartbillingpro.ui.pages;

import com.smartbillingpro.service.DashboardService;
import com.smartbillingpro.service.ReportService;
import com.smartbillingpro.ui.BarList;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.ColumnChart;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.StatCard;
import com.smartbillingpro.ui.StockStatusBar;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Business overview: key figures, trends, top sellers and stock health - all from real data. */
public final class DashboardPage extends Page {

    private final DateRangeBar range = new DateRangeBar(DateRangeBar.Preset.LAST_30);
    private final JComboBox<String> grouping = new JComboBox<>(new String[] {"Daily", "Weekly", "Monthly"});

    private final StatCard todaySales = new StatCard("Today's sales", "sales");
    private final StatCard todayProfit = new StatCard("Today's profit", "money");
    private final StatCard purchases = new StatCard("Purchases", "suppliers");
    private final StatCard inventoryValue = new StatCard("Inventory value", "inventory");
    private final StatCard products = new StatCard("Products", "inventory");
    private final StatCard lowStock = new StatCard("Low / out of stock", "warning");
    private final StatCard customers = new StatCard("Customers", "customers");
    private final StatCard receivable = new StatCard("Customers owe you", "ledgers");
    private final StatCard payable = new StatCard("You owe suppliers", "ledgers");

    private final ColumnChart salesChart = new ColumnChart("No sales in this period yet");
    private final ColumnChart profitChart = new ColumnChart("No profit or expenses in this period yet");
    private final BarList topProducts = new BarList("No products sold in this period");
    private final BarList categories = new BarList("No sales in this period");
    private final StockStatusBar stockBar = new StockStatusBar();
    private final BarList stockValue = new BarList("No stock yet");
    private final JLabel salesTitle = Ui.heading("Sales");
    private final JLabel profitTitle = Ui.heading("Profit vs expenses");
    private final JPanel cards = new JPanel(new GridLayout(0, 3, 12, 12));

    public DashboardPage(MainFrame frame) {
        super(frame, "Dashboard", "How the business is doing");
        JButton newSale = Ui.primary("New sale", "sales");
        newSale.addActionListener(e -> frame.show(MainFrame.SALES));
        addAction(newSale);

        range.onChange(this::refresh);
        grouping.addActionListener(e -> refresh());
        JPanel filters = new JPanel(new BorderLayout());
        filters.setOpaque(false);
        filters.add(range, BorderLayout.WEST);
        filters.add(Ui.flowRight(6, Ui.muted("Chart by"), grouping), BorderLayout.EAST);

        cards.setOpaque(false);
        for (StatCard c : new StatCard[] {todaySales, todayProfit, purchases, inventoryValue, products, lowStock,
            customers, receivable, payable}) {
            cards.add(c);
        }
        addCardClick(lowStock, MainFrame.INVENTORY);
        addCardClick(receivable, MainFrame.LEDGERS);
        addCardClick(payable, MainFrame.LEDGERS);
        addCardClick(customers, MainFrame.CUSTOMERS);

        JPanel charts = new JPanel(new GridLayout(0, 2, 12, 12));
        charts.setOpaque(false);
        charts.add(chartCard(salesTitle, Ui.muted("Net sales after returns, incl. tax"), salesChart));
        charts.add(chartCard(profitTitle, Ui.muted("Gross profit compared with recorded expenses"), profitChart));
        charts.add(chartCard(Ui.heading("Top-selling products"), Ui.muted("By net sales value"), topProducts));
        charts.add(chartCard(Ui.heading("Sales by category"), Ui.muted("By net sales value"), categories));
        JPanel stock = new JPanel(new BorderLayout(0, 12));
        stock.setOpaque(false);
        stock.add(stockBar, BorderLayout.NORTH);
        stock.add(stockValue, BorderLayout.CENTER);
        charts.add(chartCard(Ui.heading("Inventory overview"), Ui.muted("Stock lines by status, and stock value at cost by category"), stock));

        JPanel content = Ui.vbox(14, filters, cards, charts);
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 6));
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.BG);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        setBody(sp);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int cols = getWidth() < 900 ? 2 : 3;
                ((GridLayout) cards.getLayout()).setColumns(cols);
                ((GridLayout) charts.getLayout()).setColumns(getWidth() < 900 ? 1 : 2);
                cards.revalidate();
            }
        });
    }

    private void addCardClick(StatCard card, String target) {
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        card.setToolTipText("Open details");
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                frame.show(target);
            }
        });
    }

    private static JComponent chartCard(JLabel title, JLabel subtitle, JComponent chart) {
        Card c = new Card(new BorderLayout(0, 10));
        c.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel head = new JPanel(new BorderLayout(0, 2));
        head.setOpaque(false);
        head.add(title, BorderLayout.NORTH);
        head.add(subtitle, BorderLayout.CENTER);
        c.add(head, BorderLayout.NORTH);
        c.add(chart, BorderLayout.CENTER);
        c.setPreferredSize(new Dimension(420, 320));
        return c;
    }

    @Override
    public void refresh() {
        LocalDate from = range.from();
        LocalDate to = range.to();
        DashboardService d = ctx.dashboard();
        DashboardService.Overview o = d.overview(from, to);
        String period = range.label().toLowerCase();

        todaySales.set(Money.format(o.todaySales()), o.todayReceipts() + " receipt(s) today");
        todayProfit.set(Money.format(o.todayProfit()), "Gross profit (sales − cost)");
        todayProfit.status(o.todayProfit() < 0 ? "Loss" : null, Ui.Tone.BAD);
        purchases.set(Money.format(o.purchases()), "Stock bought, " + period);
        inventoryValue.set(Money.format(o.inventoryCost()), "At cost • retail " + Money.format(o.inventoryRetail()));
        products.set(String.format("%,d", o.productCount()), "Active products");
        lowStock.set(o.lowStock() + " / " + o.outOfStock(), "Low stock / out of stock");
        lowStock.status(o.outOfStock() > 0 ? "Restock" : o.lowStock() > 0 ? "Check" : "All good",
                o.outOfStock() > 0 ? Ui.Tone.BAD : o.lowStock() > 0 ? Ui.Tone.WARN : Ui.Tone.GOOD);
        customers.set(String.format("%,d", o.customers()), "Saved customers");
        receivable.set(Money.format(o.receivable()), "Outstanding customer payments");
        receivable.status(o.receivable() > 0 ? "Due" : "Clear", o.receivable() > 0 ? Ui.Tone.BAD : Ui.Tone.GOOD);
        payable.set(Money.format(o.payable()), "Outstanding supplier dues");
        payable.status(o.payable() > 0 ? "Due" : "Clear", o.payable() > 0 ? Ui.Tone.WARN : Ui.Tone.GOOD);

        ReportService.Grouping g = switch (grouping.getSelectedIndex()) {
            case 1 -> ReportService.Grouping.WEEK;
            case 2 -> ReportService.Grouping.MONTH;
            default -> ChronoUnit.DAYS.between(from, to) > 120 ? ReportService.Grouping.MONTH : ReportService.Grouping.DAY;
        };
        String by = g == ReportService.Grouping.DAY ? "daily" : g == ReportService.Grouping.WEEK ? "weekly" : "monthly";
        salesTitle.setText("Sales (" + by + ")  •  " + Money.format(o.periodSales()));
        profitTitle.setText("Profit vs expenses (" + by + ")");
        salesChart.setData(d.salesTrend(from, to, g).stream()
                .map(p -> new ColumnChart.Point(p.label(), p.value(), 0)).toList(), "Net sales", null);
        profitChart.setData(d.profitVsExpenses(from, to, g).stream()
                .map(p -> new ColumnChart.Point(p.label(), p.value(), p.value2())).toList(), "Gross profit", "Expenses");
        topProducts.setItems(d.topProducts(from, to, 8).stream()
                .map(p -> new BarList.Item(p.label(), p.value(), "Sold: " + Qty.format(p.value2()))).toList());
        categories.setItems(d.salesByCategory(from, to, 8).stream()
                .map(p -> new BarList.Item(p.label(), p.value(), null)).toList());
        stockBar.setCounts(d.stockStatusCounts());
        List<DashboardService.Point> sv = d.stockByCategory(6);
        stockValue.setItems(sv.stream().map(p -> new BarList.Item(p.label(), p.value(), p.value2() + " item(s)")).toList());
        setSubtitle("Today and " + period + " • updated " + com.smartbillingpro.util.Dates.formatTime(java.time.LocalDateTime.now()));
    }
}
