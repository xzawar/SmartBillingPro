package com.smartbillingpro.ui.pages;

import com.smartbillingpro.service.ReportService;
import com.smartbillingpro.service.ReportTable;
import com.smartbillingpro.ui.Card;
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
import javax.swing.JSplitPane;
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
    private DataTable<Object[]> table;
    private ReportTable current;

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
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setOpaque(false);
        right.add(head, BorderLayout.NORTH);
        right.add(Ui.card(tableHolder, 4), BorderLayout.CENTER);
        right.add(footer, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerLocation(240);
        split.setResizeWeight(0.15);
        setBody(split);
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
            table.setEmptyText(d.dated() ? "No records in this period." : "Nothing to show.");
            table.setRows(current.rows());
            table.filter(search.getText());
            tableHolder.removeAll();
            tableHolder.add(table, BorderLayout.CENTER);
            tableHolder.revalidate();
            tableHolder.repaint();
            footer.setText(footerText(current));
        });
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
