package com.smartbillingpro.ui.pages;

import com.smartbillingpro.service.ReportService;
import com.smartbillingpro.service.ReportTable;
import com.smartbillingpro.ui.BarList;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.LineChart;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.DateRangeBar;
import com.smartbillingpro.ui.ExportBar;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/** All reports in one place: pick a report, a period, then view / export / print. */
public final class ReportsPage extends Page {

    private final DefaultListModel<Object> listModel = new DefaultListModel<>();
    private final JList<Object> list = new JList<>(listModel);
    private final JLabel title = Ui.heading(" ");
    private final JLabel description = Ui.muted(" ");
    private final DateRangeBar range = new DateRangeBar(DateRangeBar.Preset.THIS_MONTH);
    private final JTextField search = Ui.search("Search in this report");
    private final JPanel tableHolder = new JPanel(new BorderLayout());
    private final JLabel footer = new JLabel(" ");
    private Card chartCard;
    private DataTable<Object[]> table;
    private ReportTable current;
    private final LineChart trend = new LineChart("Nothing recorded in this period");
    private final BarList bars = new BarList("Nothing recorded in this period");
    private final java.awt.CardLayout chartCards = new java.awt.CardLayout();
    private final JPanel chartHolder = new JPanel(chartCards);

    /** What to draw above a report's table: a trend of one or two columns, or the top rows as bars. */
    private record ChartSpec(boolean trend, String valueColumn, String secondColumn) {
    }

    private static final java.util.Map<String, ChartSpec> CHARTS = java.util.Map.ofEntries(
            java.util.Map.entry("sales.daily", new ChartSpec(true, "Net sales", null)),
            java.util.Map.entry("sales.weekly", new ChartSpec(true, "Net sales", null)),
            java.util.Map.entry("sales.monthly", new ChartSpec(true, "Net sales", null)),
            java.util.Map.entry("profit.daily", new ChartSpec(true, "Gross profit", "Net profit")),
            java.util.Map.entry("profit.monthly", new ChartSpec(true, "Gross profit", "Net profit")),
            java.util.Map.entry("sales.products", new ChartSpec(false, "Net sales", null)),
            java.util.Map.entry("sales.categories", new ChartSpec(false, "Net sales", null)),
            java.util.Map.entry("sales.methods", new ChartSpec(false, "Sales total", null)),
            java.util.Map.entry("profit.products", new ChartSpec(false, "Profit", null)),
            java.util.Map.entry("stock.valuation", new ChartSpec(false, "Cost value", null)),
            java.util.Map.entry("fin.receivables", new ChartSpec(false, "Balance", null)),
            java.util.Map.entry("fin.payables", new ChartSpec(false, "Balance", null)),
            java.util.Map.entry("fin.methods", new ChartSpec(false, "Net", null)),
            java.util.Map.entry("fin.expenses", new ChartSpec(false, "Amount", null)));

    public ReportsPage(MainFrame frame) {
        super(frame, "Reports", "Sales, profit, stock and money – for any period");
        String group = null;
        for (ReportService.Definition d : ReportService.CATALOG) {
            if (!d.group().equals(group)) {
                group = d.group();
                listModel.addElement(group);
            }
            listModel.addElement(d);
        }
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                if (v instanceof String header) {
                    JLabel h = new JLabel(header.toUpperCase());
                    h.setFont(Theme.bold(11));
                    h.setForeground(Theme.MUTED);
                    h.setBorder(BorderFactory.createEmptyBorder(i == 0 ? 6 : 16, 12, 4, 8));
                    return h;
                }
                ReportService.Definition d = (ReportService.Definition) v;
                super.getListCellRendererComponent(l, d.title(), i, sel, false);
                setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 8));
                setFont(sel ? Theme.bold(13) : Theme.font(13));
                return this;
            }
        });
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            if (list.getSelectedValue() instanceof String) {
                int i = list.getSelectedIndex();
                list.setSelectedIndex(Math.min(i + 1, listModel.size() - 1));
                return;
            }
            run();
        });
        range.onChange(this::run);
        Ui.onChange(search, () -> {
            if (table != null) {
                table.filter(search.getText());
            }
        });
        JScrollPane ls = new JScrollPane(list);
        ls.setBorder(BorderFactory.createEmptyBorder());
        Card left = new Card(new BorderLayout());
        left.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
        left.add(ls, BorderLayout.CENTER);
        left.setMinimumSize(new Dimension(200, 200));

        JPanel head = new JPanel(new BorderLayout(0, 8));
        head.setOpaque(false);
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.setOpaque(false);
        titles.add(title, BorderLayout.NORTH);
        titles.add(description, BorderLayout.CENTER);
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        top.add(titles, BorderLayout.CENTER);
        top.add(ExportBar.create(ctx, this, () -> current), BorderLayout.EAST);
        head.add(top, BorderLayout.NORTH);
        search.setColumns(18);
        head.add(Ui.flowLeft(8, range, search), BorderLayout.CENTER);
        tableHolder.setOpaque(false);
        footer.setFont(Theme.bold(13));
        footer.setForeground(Theme.TEXT);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 4));
        bars.setFormat(com.smartbillingpro.util.Money::format,
                v -> com.smartbillingpro.util.Money.symbol() + LineChart.compact(v));
        trend.setPreferredSize(new Dimension(300, 190));
        bars.setPreferredSize(new Dimension(300, 190));
        chartHolder.setOpaque(false);
        chartHolder.add(trend, "trend");
        chartHolder.add(bars, "bars");
        chartHolder.add(new JPanel() {
            {
                setOpaque(false);
            }
        }, "none");
        Card chartCard = new Card(new BorderLayout());
        chartCard.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        chartCard.add(chartHolder, BorderLayout.CENTER);
        chartCard.setPreferredSize(new Dimension(300, 214));
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(chartCard, BorderLayout.NORTH);
        center.add(Ui.card(tableHolder, 4), BorderLayout.CENTER);
        chartCard.putClientProperty("chart-card", Boolean.TRUE);
        this.chartCard = chartCard;
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false);
        right.add(head, BorderLayout.NORTH);
        right.add(center, BorderLayout.CENTER);
        right.add(footer, BorderLayout.SOUTH);
        left.setPreferredSize(new Dimension(230, 200));
        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        body.add(left, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);
        setBody(body);
        list.setSelectedIndex(1);
    }

    private static DataTable.Kind kind(ReportTable.Type t) {
        return switch (t) {
            case MONEY -> DataTable.Kind.MONEY;
            case QTY -> DataTable.Kind.QTY;
            case INT -> DataTable.Kind.INT;
            case DATE -> DataTable.Kind.DATE;
            case DATETIME -> DataTable.Kind.DATETIME;
            case PERCENT -> DataTable.Kind.PERCENT;
            case TEXT -> DataTable.Kind.TEXT;
        };
    }

    private void run() {
        if (!(list.getSelectedValue() instanceof ReportService.Definition d)) {
            return;
        }
        range.setVisible(d.dated());
        title.setText(d.title());
        description.setText(d.description());
        safely(() -> {
            current = ctx.reports().run(d.id(), range.from(), range.to());
            table = new DataTable<>();
            for (int i = 0; i < current.columns().size(); i++) {
                final int col = i;
                ReportTable.Column c = current.columns().get(i);
                table.col(c.name(), kind(c.type()), r -> col < r.length ? r[col] : null, c.weight() * 45);
            }
            table.setEmptyText(d.dated() ? "Nothing recorded in " + range.label().toLowerCase()
                    + ". Try a longer period." : "Nothing to show yet.");
            table.setRows(current.rows());
            table.filter(search.getText());
            tableHolder.removeAll();
            tableHolder.add(table, BorderLayout.CENTER);
            tableHolder.revalidate();
            tableHolder.repaint();
            footer.setText(footerText(current));
            showChart(d.id());
        });
    }

    private void showChart(String id) {
        ChartSpec spec = CHARTS.get(id);
        int a = spec == null ? -1 : column(spec.valueColumn());
        if (spec == null || a < 0 || current.rows().isEmpty()) {
            chartCard.setVisible(false);
            return;
        }
        chartCard.setVisible(true);
        int b = spec.secondColumn() == null ? -1 : column(spec.secondColumn());
        if (spec.trend()) {
            java.util.List<LineChart.Point> pts = new java.util.ArrayList<>();
            for (Object[] r : current.rows()) {
                pts.add(new LineChart.Point(String.valueOf(r[0]), num(r[a]), b < 0 ? 0 : num(r[b])));
            }
            trend.setData(pts, spec.valueColumn(), b < 0 ? null : spec.secondColumn());
            chartCards.show(chartHolder, "trend");
        } else {
            java.util.List<BarList.Item> items = current.rows().stream()
                    .filter(r -> num(r[a]) > 0)
                    .sorted((x, y) -> Long.compare(num(y[a]), num(x[a])))
                    .limit(8)
                    .map(r -> new BarList.Item(String.valueOf(r[0]), num(r[a]), null)).toList();
            bars.setItems(items);
            chartCards.show(chartHolder, "bars");
        }
    }

    private int column(String name) {
        for (int i = 0; i < current.columns().size(); i++) {
            if (current.columns().get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static String footerText(ReportTable t) {
        StringBuilder sb = new StringBuilder("<html>");
        for (String[] s : t.summaryLines()) {
            sb.append(s[0]).append(": <b>").append(s[1]).append("</b>&nbsp;&nbsp;&nbsp;&nbsp;");
        }
        Object[] totals = t.totals();
        if (totals != null) {
            for (int i = 0; i < t.columns().size() && i < totals.length; i++) {
                ReportTable.Column c = t.columns().get(i);
                if (totals[i] != null && c.type().numeric()) {
                    sb.append(c.name()).append(": <b>").append(ReportTable.format(totals[i], c.type()))
                            .append("</b>&nbsp;&nbsp;&nbsp;&nbsp;");
                } else if (i == 0 && totals[i] != null) {
                    sb.append("<b>").append(totals[i]).append("</b>&nbsp;&nbsp;—&nbsp;&nbsp;");
                } else if (totals[i] instanceof String s && !s.isBlank() && i > 0) {
                    sb.append(s).append("&nbsp;&nbsp;&nbsp;&nbsp;");
                }
            }
        }
        return sb.append("</html>").toString();
    }

    @Override
    public void refresh() {
        run();
    }
}
