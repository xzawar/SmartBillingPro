package com.smartbillingpro.ui.pages;

import com.smartbillingpro.service.DashboardService;
import com.smartbillingpro.service.ReportService;
import com.smartbillingpro.service.Settings;
import com.smartbillingpro.ui.BarList;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.EmptyState;
import com.smartbillingpro.ui.Icons;
import com.smartbillingpro.ui.LineChart;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.StatCard;
import com.smartbillingpro.ui.StockStatusBar;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.flows.ProductFlow;
import com.smartbillingpro.ui.flows.ShopSetupFlow;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** How the shop is doing, from real data only, plus a short "get ready" checklist for new shops. */
public final class DashboardPage extends Page {

    private final DateRangeBar range = new DateRangeBar(DateRangeBar.Preset.LAST_30);
    private final JComboBox<String> grouping = new JComboBox<>(new String[] {"Day by day", "Week by week", "Month by month"});

    private final StatCard todaySales = new StatCard("Sales today", "sales", Theme.Tint.GREEN);
    private final StatCard todayProfit = new StatCard("Profit today", "money", Theme.Tint.TEAL);
    private final StatCard lowStock = new StatCard("Running low", "warning", Theme.Tint.YELLOW);
    private final StatCard receivable = new StatCard("Customers owe you", "customers", Theme.Tint.RED);
    private final StatCard payable = new StatCard("You owe suppliers", "suppliers", Theme.Tint.ORANGE);
    private final StatCard inventoryValue = new StatCard("Stock value", "inventory", Theme.Tint.BLUE);

    private final LineChart salesChart = new LineChart("No sales in this period yet");
    private final LineChart profitChart = new LineChart("No profit or expenses in this period yet");
    private final BarList payments = new BarList("No money received in this period");
    private final BarList topProducts = new BarList("Nothing sold in this period");
    private final BarList categories = new BarList("No sales in this period");
    private final StockStatusBar stockBar = new StockStatusBar();
    private final BarList stockValue = new BarList("No stock yet");
    private final JLabel salesTitle = Ui.heading("Sales");
    private final JLabel profitTitle = Ui.heading("Profit and expenses");
    private final JPanel cards = new JPanel(new GridLayout(0, 3, 12, 12));
    private final JPanel charts = new JPanel(new GridLayout(0, 2, 12, 12));
    private final JPanel checklistHolder = new JPanel(new BorderLayout());
    private final JComponent noSales;
    private final JPanel filters = new JPanel(new BorderLayout());

    public DashboardPage(MainFrame frame) {
        super(frame, "Dashboard", "How the shop is doing");
        JButton newSale = Ui.primary("New sale", "sales");
        newSale.addActionListener(e -> frame.show(MainFrame.SALES));
        addAction(newSale);

        range.onChange(this::refresh);
        grouping.addActionListener(e -> refresh());
        filters.setOpaque(false);
        filters.add(range, BorderLayout.WEST);
        filters.add(Ui.flowRight(6, Ui.muted("Show charts"), grouping), BorderLayout.EAST);

        cards.setOpaque(false);
        for (StatCard c : new StatCard[] {todaySales, todayProfit, lowStock, receivable, payable, inventoryValue}) {
            cards.add(c);
        }
        onClick(lowStock, () -> {
            InventoryPage inv = frame.page(MainFrame.INVENTORY);
            frame.show(MainFrame.INVENTORY);
            inv.showLowStock();
        }, "See what is running low");
        onClick(receivable, () -> frame.show(MainFrame.CUSTOMERS), "See who owes you");
        onClick(payable, () -> frame.show(MainFrame.SUPPLIERS), "See who you owe");
        onClick(inventoryValue, () -> frame.show(MainFrame.INVENTORY), "Open Inventory");

        charts.setOpaque(false);
        payments.setFormat(Money::format, v -> Money.symbol() + LineChart.compact(v));
        charts.add(chartCard(salesTitle, Ui.muted("Money taken after returns"), salesChart));
        charts.add(chartCard(profitTitle, Ui.muted("Profit on what you sold, next to what the shop spent"), profitChart));
        charts.add(chartCard(Ui.heading("How customers paid"), Ui.muted("Money received, by way of paying"), payments));
        charts.add(chartCard(Ui.heading("Best sellers"), Ui.muted("By money taken"), topProducts));
        charts.add(chartCard(Ui.heading("Sales by category"), Ui.muted("By money taken"), categories));
        JPanel stock = new JPanel(new BorderLayout(0, 12));
        stock.setOpaque(false);
        stock.add(stockBar, BorderLayout.NORTH);
        stock.add(stockValue, BorderLayout.CENTER);
        charts.add(chartCard(Ui.heading("Your stock"), Ui.muted("How many items are fine, low or out – and stock value by category"), stock));

        Card empty = new Card(new BorderLayout());
        empty.add(new EmptyState("reports", "Your charts will appear here",
                "Sales, profit and best sellers are worked out from your real sales. Make your first sale to see them.", true)
                .action("Go to Sales", "sales", () -> frame.show(MainFrame.SALES)), BorderLayout.CENTER);
        empty.setPreferredSize(new Dimension(400, 280));
        noSales = empty;

        checklistHolder.setOpaque(false);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[] {checklistHolder, filters, cards, charts, noSales}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(c);
            content.add(javax.swing.Box.createVerticalStrut(14));
        }
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 6));
        JPanel wrap = new com.smartbillingpro.ui.ScrollablePanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(content, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.BG);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        setBody(sp);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ((GridLayout) cards.getLayout()).setColumns(getWidth() < 900 ? 2 : 3);
                ((GridLayout) charts.getLayout()).setColumns(getWidth() < 900 ? 1 : 2);
                cards.revalidate();
                charts.revalidate();
            }
        });
    }

    private void onClick(StatCard card, Runnable action, String tip) {
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        card.setToolTipText(tip);
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                action.run();
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

    // ------------------------------------------------------------ "get your shop ready"

    private record Step(String title, String hint, boolean done, String button, Runnable action) {
    }

    private List<Step> steps(DashboardService.Overview o, boolean anySale) {
        Settings st = ctx.settings().get();
        List<Step> list = new ArrayList<>();
        list.add(new Step("Add your shop's name", "It is printed on every receipt.", !"My Store".equals(st.storeName()),
                "Add details", () -> {
                    ShopSetupFlow.open(this, ctx);
                    frame.storeNameChanged();
                    refresh();
                }));
        list.add(new Step("Add your first product", "One question at a time – it takes seconds.",
                o.productCount() > 0, "Add a product", () -> {
                    ProductFlow.Result r = ProductFlow.add(this, ctx, null, null);
                    if (r.productId() != null) {
                        Toast.ok(this, "Product added.");
                    }
                    refresh();
                }));
        list.add(new Step("Make your first sale", "Scan or tap products, then press Complete sale.", anySale,
                "Go to Sales", () -> frame.show(MainFrame.SALES)));
        list.add(new Step("Choose your receipt printer", "Or keep saving receipts as PDF.", !st.printerName().isBlank(),
                "Printer settings", () -> {
                    SettingsPage s = frame.page(MainFrame.SETTINGS);
                    frame.show(MainFrame.SETTINGS);
                    s.showPrinterTab();
                }));
        list.add(new Step("Make your first backup", "So your records are safe if the computer breaks.",
                !ctx.backups().lastBackupTime().isBlank(), "Back up", () -> {
                    SettingsPage s = frame.page(MainFrame.SETTINGS);
                    frame.show(MainFrame.SETTINGS);
                    s.showBackupTab();
                }));
        return list;
    }

    private void buildChecklist(List<Step> list) {
        checklistHolder.removeAll();
        long done = list.stream().filter(Step::done).count();
        if (ctx.settings().get().checklistHidden() || done == list.size()) {
            checklistHolder.setVisible(false);
            return;
        }
        checklistHolder.setVisible(true);
        Card card = new Card(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
        JLabel title = new JLabel("Get your shop ready");
        title.setFont(Theme.bold(17));
        title.setForeground(Theme.TEXT);
        JLabel progress = Ui.muted(done + " of " + list.size() + " done");
        final double fraction = done / (double) list.size();
        JComponent bar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.NEUTRAL_SOFT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(Theme.ACCENT_INK);
                g2.fillRoundRect(0, 0, (int) (getWidth() * fraction), getHeight(), getHeight(), getHeight());
                g2.dispose();
            }
        };
        bar.setPreferredSize(new Dimension(160, 6));
        JButton hide = Ui.ghost("Hide", null);
        hide.setToolTipText("Hide this list. Everything here can also be done from the menu on the left.");
        hide.addActionListener(e -> safely(() -> {
            ctx.settings().save(Map.of(Settings.CHECKLIST_HIDDEN, "true"));
            refresh();
        }));
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.add(Ui.vbox(4, title, Ui.flowLeft(10, bar, progress)), BorderLayout.WEST);
        head.add(hide, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 2));
        rows.setOpaque(false);
        boolean firstOpen = true;
        List<String> finished = new ArrayList<>();
        for (Step s : list) {
            if (s.done()) {
                finished.add(s.title());
                continue;
            }
            rows.add(stepRow(s, firstOpen));
            firstOpen = false;
        }
        if (!finished.isEmpty()) {
            JLabel doneLine = new JLabel("\u2713  Done: " + String.join("  \u2022  ", finished));
            doneLine.setFont(Theme.font(12));
            doneLine.setForeground(Theme.GOOD);
            doneLine.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            card.add(doneLine, BorderLayout.SOUTH);
        }
        card.add(rows, BorderLayout.CENTER);
        checklistHolder.add(card, BorderLayout.CENTER);
    }

    private JComponent stepRow(Step s, boolean next) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        JComponent mark = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = 24;
                int y = (getHeight() - d) / 2;
                if (s.done()) {
                    g2.setColor(Theme.ACCENT);
                    g2.fillOval(0, y, d, d);
                    Icons.get("check", 14, Theme.ACCENT_INK).paintIcon(this, g2, 5, y + 5);
                } else {
                    g2.setColor(Theme.BORDER_STRONG);
                    g2.setStroke(new java.awt.BasicStroke(1.6f));
                    g2.drawOval(1, y + 1, d - 2, d - 2);
                }
                g2.dispose();
            }
        };
        mark.setPreferredSize(new Dimension(26, 26));
        JLabel t = new JLabel(s.title());
        t.setFont(Theme.bold(14));
        t.setForeground(s.done() ? Theme.MUTED : Theme.TEXT);
        JLabel h = Ui.muted(s.done() ? "Done" : s.hint());
        h.setFont(Theme.font(12));
        JPanel text = new JPanel(new BorderLayout(0, 1));
        text.setOpaque(false);
        text.add(t, BorderLayout.NORTH);
        text.add(h, BorderLayout.CENTER);
        row.add(mark, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        if (!s.done()) {
            JButton b = next ? Ui.primary(s.button(), null) : Ui.secondary(s.button(), null);
            b.addActionListener(e -> s.action().run());
            row.add(b, BorderLayout.EAST);
        }
        return row;
    }

    // ------------------------------------------------------------ data

    @Override
    public void refresh() {
        LocalDate from = range.from();
        LocalDate to = range.to();
        DashboardService d = ctx.dashboard();
        DashboardService.Overview o = d.overview(from, to);
        boolean anySale = !ctx.sales().list(null, null, null, null, null, null, 1).isEmpty();
        buildChecklist(steps(o, anySale));
        String period = range.label().toLowerCase();

        todaySales.set(Money.format(o.todaySales()), o.todayReceipts() == 1 ? "1 receipt today"
                : o.todayReceipts() + " receipts today");
        todayProfit.set(Money.format(o.todayProfit()), "After the cost of what was sold");
        todayProfit.status(o.todayProfit() < 0 ? "Loss" : null, Ui.Tone.BAD);
        lowStock.set(String.valueOf(o.lowStock() + o.outOfStock()), o.outOfStock() + " out of stock, " + o.lowStock() + " low");
        lowStock.status(o.outOfStock() > 0 ? "Restock" : o.lowStock() > 0 ? "Check" : "All good",
                o.outOfStock() > 0 ? Ui.Tone.BAD : o.lowStock() > 0 ? Ui.Tone.WARN : Ui.Tone.GOOD);
        receivable.set(Money.format(o.receivable()), o.receivable() > 0 ? "Not paid yet" : "Nobody owes you");
        receivable.status(o.receivable() > 0 ? "To collect" : null, Ui.Tone.WARN);
        payable.set(Money.format(o.payable()), o.payable() > 0 ? "Still to pay" : "You don't owe anyone");
        payable.status(o.payable() > 0 ? "To pay" : null, Ui.Tone.WARN);
        inventoryValue.set(Money.format(o.inventoryCost()), "At buying price  •  sells for " + Money.format(o.inventoryRetail()));

        filters.setVisible(anySale);
        charts.setVisible(anySale);
        noSales.setVisible(!anySale);
        if (anySale) {
            ReportService.Grouping g = switch (grouping.getSelectedIndex()) {
                case 1 -> ReportService.Grouping.WEEK;
                case 2 -> ReportService.Grouping.MONTH;
                default -> ChronoUnit.DAYS.between(from, to) > 120 ? ReportService.Grouping.MONTH : ReportService.Grouping.DAY;
            };
            salesTitle.setText("Sales  •  " + Money.format(o.periodSales()));
            profitTitle.setText("Profit " + Money.format(o.periodProfit()) + "  •  spent " + Money.format(o.expenses()));
            salesChart.setData(d.salesTrend(from, to, g).stream()
                    .map(p -> new LineChart.Point(p.label(), p.value(), 0)).toList(), "Sales", null);
            profitChart.setData(d.profitVsExpenses(from, to, g).stream()
                    .map(p -> new LineChart.Point(p.label(), p.value(), p.value2())).toList(), "Profit", "Expenses");
            payments.setItems(d.paymentSplit(from, to).stream()
                    .map(p -> new BarList.Item(p.label(), p.value(), null)).toList());
            topProducts.setItems(d.topProducts(from, to, 8).stream()
                    .map(p -> new BarList.Item(p.label(), p.value(), Qty.format(p.value2()) + " sold")).toList());
            categories.setItems(d.salesByCategory(from, to, 8).stream()
                    .map(p -> new BarList.Item(p.label(), p.value(), null)).toList());
            stockBar.setCounts(d.stockStatusCounts());
            List<DashboardService.Point> sv = d.stockByCategory(6);
            stockValue.setItems(sv.stream().map(p -> new BarList.Item(p.label(), p.value(),
                    p.value2() == 1 ? "1 item" : p.value2() + " items")).toList());
        }
        setSubtitle(anySale ? "Today, and " + period + "  •  updated "
                + com.smartbillingpro.util.Dates.formatTime(java.time.LocalDateTime.now()) : "How the shop is doing");
    }
}
