package com.smartbillingpro.service;

import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A rendered report: typed columns, rows, an optional totals row and optional
 * summary lines. The same object is shown in the app and exported to CSV,
 * PDF or the printer, so all three always show identical numbers.
 */
public final class ReportTable {

    public enum Type {
        TEXT, MONEY, QTY, INT, DATE, DATETIME, PERCENT;

        public boolean numeric() {
            return this == MONEY || this == QTY || this == INT || this == PERCENT;
        }
    }

    public record Column(String name, Type type, int weight) {
        public Column(String name, Type type) {
            this(name, type, type == Type.TEXT ? 3 : 2);
        }
    }

    private final String title;
    private String subtitle = "";
    private final List<Column> columns = new ArrayList<>();
    private final List<Object[]> rows = new ArrayList<>();
    private final List<String[]> summary = new ArrayList<>();
    private Object[] totals;

    public ReportTable(String title) {
        this.title = title;
    }

    public ReportTable col(String name, Type type) {
        columns.add(new Column(name, type));
        return this;
    }

    public ReportTable col(String name, Type type, int weight) {
        columns.add(new Column(name, type, weight));
        return this;
    }

    public void row(Object... values) {
        rows.add(values);
    }

    public void setTotals(Object... values) {
        this.totals = values;
    }

    public void summary(String label, String value) {
        summary.add(new String[] {label, value});
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public List<Column> columns() {
        return columns;
    }

    public List<Object[]> rows() {
        return rows;
    }

    public Object[] totals() {
        return totals;
    }

    public List<String[]> summaryLines() {
        return summary;
    }

    /** Sum a numeric column over all rows (for building a totals row). */
    public long sum(int column) {
        long total = 0;
        for (Object[] r : rows) {
            if (r[column] instanceof Number n) {
                total += n.longValue();
            }
        }
        return total;
    }

    /** Display text for one cell. */
    public static String format(Object value, Type type) {
        if (value == null) {
            return "";
        }
        return switch (type) {
            case MONEY -> value instanceof Number n ? Money.grouped(n.longValue()) : value.toString();
            case QTY -> value instanceof Number n ? Qty.format(n.longValue()) : value.toString();
            case INT -> value instanceof Number n ? String.format("%,d", n.longValue()) : value.toString();
            case DATE -> value.toString().length() >= 10 && value.toString().charAt(4) == '-'
                    ? Dates.formatDate(value.toString().length() == 10 ? value + " 00:00:00" : value.toString())
                    : value.toString();
            case DATETIME -> Dates.formatDateTime(value.toString());
            case PERCENT -> value instanceof BigDecimal b ? b.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + "%"
                    : value + "%";
            case TEXT -> value.toString();
        };
    }

    /** Raw value for CSV export (numbers without grouping, in major units). */
    public static String csvValue(Object value, Type type) {
        if (value == null) {
            return "";
        }
        return switch (type) {
            case MONEY -> value instanceof Number n ? Money.plain(n.longValue()) : value.toString();
            case QTY -> value instanceof Number n ? Qty.toDecimal(n.longValue()).stripTrailingZeros().toPlainString()
                    : value.toString();
            case PERCENT -> value instanceof BigDecimal b ? b.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    : value.toString();
            default -> format(value, type);
        };
    }
}
