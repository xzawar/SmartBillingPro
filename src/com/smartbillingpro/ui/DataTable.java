package com.smartbillingpro.ui;

import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A sortable, searchable table bound to a list of objects. Columns know their
 * kind (text, money, quantity, date, status) so numbers are right-aligned and
 * formatted consistently everywhere.
 */
public class DataTable<T> extends JPanel {

    public enum Kind {
        TEXT, MONEY, SIGNED_MONEY, QTY, INT, DATE, DATETIME, PERCENT, STATUS
    }

    /** A status cell: label plus tone. */
    public record Status(String text, Ui.Tone tone) implements Comparable<Status> {
        @Override
        public int compareTo(Status o) {
            return text.compareTo(o.text);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private record Col<T>(String name, Kind kind, Function<T, Object> value, int width) {
    }

    private final List<Col<T>> cols = new ArrayList<>();
    private final List<T> rows = new ArrayList<>();
    private final Model model = new Model();
    private final JTable table;
    private final TableRowSorter<Model> sorter;
    private String emptyText = "Nothing to show yet.";
    private String filterText = "";

    public DataTable() {
        super(new BorderLayout());
        setOpaque(false);
        table = new JTable(model) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getRowCount() == 0) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(Theme.font(13));
                    g2.setColor(Theme.MUTED);
                    String text = filterText.isBlank() ? emptyText : "No results for \"" + filterText + "\".";
                    int w = g2.getFontMetrics().stringWidth(text);
                    g2.drawString(text, Math.max(12, (getVisibleRect().width - w) / 2), 54);
                    g2.dispose();
                }
            }

            @Override
            public String getToolTipText(MouseEvent e) {
                int r = rowAtPoint(e.getPoint());
                int c = columnAtPoint(e.getPoint());
                if (r < 0 || c < 0) {
                    return null;
                }
                Component comp = prepareRenderer(getCellRenderer(r, c), r, c);
                if (comp instanceof JLabel l && l.getPreferredSize().width > getColumnModel().getColumn(c).getWidth()) {
                    return l.getText();
                }
                return null;
            }
        };
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(false);
        table.setFont(Theme.font(13));
        table.setBackground(Theme.SURFACE);
        table.setToolTipText("");
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-row");
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        add(sp, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------ columns

    public DataTable<T> col(String name, Kind kind, Function<T, Object> value, int width) {
        cols.add(new Col<>(name, kind, value, width));
        model.fireTableStructureChanged();
        applyColumns();
        return this;
    }

    public DataTable<T> text(String name, Function<T, Object> value, int width) {
        return col(name, Kind.TEXT, value, width);
    }

    public DataTable<T> money(String name, Function<T, Object> value) {
        return col(name, Kind.MONEY, value, 110);
    }

    public DataTable<T> signedMoney(String name, Function<T, Object> value) {
        return col(name, Kind.SIGNED_MONEY, value, 120);
    }

    public DataTable<T> qty(String name, Function<T, Object> value) {
        return col(name, Kind.QTY, value, 80);
    }

    public DataTable<T> status(String name, Function<T, Object> value) {
        return col(name, Kind.STATUS, value, 120);
    }

    private void applyColumns() {
        for (int i = 0; i < cols.size() && i < table.getColumnModel().getColumnCount(); i++) {
            Col<T> c = cols.get(i);
            TableColumn tc = table.getColumnModel().getColumn(i);
            tc.setPreferredWidth(c.width());
            tc.setCellRenderer(new Renderer(c.kind()));
            boolean numeric = c.kind() != Kind.TEXT && c.kind() != Kind.STATUS;
            tc.setHeaderRenderer((t, v, sel, foc, r, col) -> {
                Component comp = t.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(t, v, sel, foc, r, col);
                if (comp instanceof JLabel l) {
                    l.setHorizontalAlignment(numeric ? SwingConstants.RIGHT : SwingConstants.LEFT);
                }
                return comp;
            });
            final Kind k = c.kind();
            sorter.setComparator(i, (Comparator<Object>) (a, b) -> compare(a, b, k));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object a, Object b, Kind k) {
        if (a == null) {
            return b == null ? 0 : -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue());
        }
        if (a instanceof Comparable ca && a.getClass().isInstance(b)) {
            return ca.compareTo(b);
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }

    // ------------------------------------------------------------ data

    public void setRows(List<T> data) {
        T keep = selected();
        rows.clear();
        rows.addAll(data);
        model.fireTableDataChanged();
        if (keep != null) {
            select(keep);
        }
    }

    public List<T> rows() {
        return rows;
    }

    public void setEmptyText(String text) {
        this.emptyText = text;
    }

    /** Show only rows where any cell contains {@code text} (case-insensitive). */
    public void filter(String text) {
        filterText = text == null ? "" : text.trim();
        if (filterText.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }
        String needle = filterText.toLowerCase();
        sorter.setRowFilter(new RowFilter<Model, Integer>() {
            @Override
            public boolean include(Entry<? extends Model, ? extends Integer> entry) {
                for (int i = 0; i < entry.getValueCount(); i++) {
                    Object v = entry.getValue(i);
                    if (v != null && format(v, cols.get(i).kind()).toLowerCase().contains(needle)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    public T selected() {
        int r = table.getSelectedRow();
        if (r < 0) {
            return null;
        }
        int m = table.convertRowIndexToModel(r);
        return m >= 0 && m < rows.size() ? rows.get(m) : null;
    }

    public void select(T item) {
        int idx = rows.indexOf(item);
        if (idx >= 0) {
            int v = table.convertRowIndexToView(idx);
            if (v >= 0) {
                table.setRowSelectionInterval(v, v);
                table.scrollRectToVisible(table.getCellRect(v, 0, true));
            }
        }
    }

    public void selectFirst() {
        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    public void onOpen(Consumer<T> action) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilitiesHelper.isLeft(e)) {
                    T t = selected();
                    if (t != null) {
                        action.accept(t);
                    }
                }
            }
        });
        table.getActionMap().put("open-row", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                T t = selected();
                if (t != null) {
                    action.accept(t);
                }
            }
        });
    }

    public void onSelect(Consumer<T> action) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                action.accept(selected());
            }
        });
    }

    public JTable table() {
        return table;
    }

    private static final class SwingUtilitiesHelper {
        static boolean isLeft(MouseEvent e) {
            return javax.swing.SwingUtilities.isLeftMouseButton(e);
        }
    }

    // ------------------------------------------------------------ formatting

    static String format(Object v, Kind k) {
        if (v == null) {
            return "";
        }
        return switch (k) {
            case MONEY, SIGNED_MONEY -> v instanceof Number n ? Money.grouped(n.longValue()) : v.toString();
            case QTY -> v instanceof Number n ? Qty.format(n.longValue()) : v.toString();
            case INT -> v instanceof Number n ? String.format("%,d", n.longValue()) : v.toString();
            case DATE -> Dates.formatDate(v.toString().length() == 10 ? v + " 00:00:00" : v.toString());
            case DATETIME -> Dates.formatDateTime(v.toString());
            case PERCENT -> v instanceof BigDecimal b ? b.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%" : v + "%";
            case STATUS, TEXT -> v.toString();
        };
    }

    private final class Model extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols.size();
        }

        @Override
        public String getColumnName(int column) {
            return cols.get(column).name();
        }

        @Override
        public Object getValueAt(int row, int column) {
            return cols.get(column).value().apply(rows.get(row));
        }
    }

    private static final class Renderer extends DefaultTableCellRenderer {
        private final Kind kind;
        private Status status;

        Renderer(Kind kind) {
            this.kind = kind;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, format(v, kind), sel, false, r, c);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            boolean numeric = kind != Kind.TEXT && kind != Kind.STATUS;
            setHorizontalAlignment(numeric ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setForeground(Theme.TEXT);
            setFont(Theme.font(13));
            status = null;
            if (kind == Kind.SIGNED_MONEY && v instanceof Number n && n.longValue() != 0) {
                setForeground(n.longValue() > 0 ? Theme.BAD : Theme.GOOD);
                setFont(Theme.bold(13));
            }
            if (kind == Kind.STATUS && v instanceof Status s) {
                status = s;
                setText(s.text());
                setForeground(s.tone().fg);
                setFont(Theme.bold(11));
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (status != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int tw = getFontMetrics(getFont()).stringWidth(status.text()) + 18;
                int h = 22;
                int y = (getHeight() - h) / 2;
                g2.setColor(status.tone().bg);
                g2.fillRoundRect(8, y, Math.min(tw, getWidth() - 12), h, h, h);
                g2.setColor(status.tone().fg);
                g2.setFont(getFont());
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.drawString(status.text(), 17, y + 15);
                g2.dispose();
                return;
            }
            super.paintComponent(g);
        }
    }
}
