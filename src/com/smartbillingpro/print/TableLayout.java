package com.smartbillingpro.print;

import com.smartbillingpro.service.ReportTable;
import com.smartbillingpro.util.Dates;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Paginated layout of a {@link ReportTable} for PDF export and printing. */
public final class TableLayout {

    private static final Color INK = new Color(0x1F, 0x24, 0x2C);
    private static final Color MUTED = new Color(0x5F, 0x67, 0x73);
    private static final Color RULE = new Color(0xDD, 0xE1, 0xE7);
    private static final Color BAND = new Color(0xF1, 0xF4, 0xF8);
    private static final float MARGIN = 36f;

    private final ReportTable table;
    private final float pageW;
    private final float pageH;
    private final DocFont body = DocFont.regular(8.5f);
    private final DocFont bold = DocFont.bold(8.5f);
    private final DocFont head = DocFont.bold(7.5f);
    private List<int[]> pages;

    public TableLayout(ReportTable table, float pageW, float pageH) {
        this.table = table;
        this.pageW = pageW;
        this.pageH = pageH;
    }

    /** Landscape when the report has many columns. */
    public static boolean prefersLandscape(ReportTable t) {
        int weight = t.columns().stream().mapToInt(ReportTable.Column::weight).sum();
        return t.columns().size() > 7 || weight > 20;
    }

    private float rowH(DocCanvas c) {
        return c.lineHeight(body) + 6;
    }

    private float headerHeight(boolean first) {
        float h = 58;
        if (first) {
            h += table.summaryLines().size() * 13 + (table.summaryLines().isEmpty() ? 0 : 8);
        }
        return h + 22;
    }

    public int pageCount(DocCanvas c) {
        return paginate(c).size();
    }

    private List<int[]> paginate(DocCanvas c) {
        if (pages != null) {
            return pages;
        }
        pages = new ArrayList<>();
        int n = table.rows().size();
        int i = 0;
        boolean first = true;
        float rowH = rowH(c);
        while (true) {
            float avail = pageH - 2 * MARGIN - headerHeight(first) - 20;
            int cap = Math.max(1, (int) (avail / rowH));
            int capLast = Math.max(0, (int) ((avail - (table.totals() == null ? 0 : rowH + 4)) / rowH));
            int remaining = n - i;
            if (remaining <= capLast) {
                pages.add(new int[] {i, n});
                return pages;
            }
            int take = Math.min(cap, remaining);
            pages.add(new int[] {i, i + take});
            i += take;
            first = false;
        }
    }

    private float[] columnX(float width) {
        int total = table.columns().stream().mapToInt(ReportTable.Column::weight).sum();
        float[] x = new float[table.columns().size() + 1];
        x[0] = MARGIN;
        for (int i = 0; i < table.columns().size(); i++) {
            x[i + 1] = x[i] + width * table.columns().get(i).weight() / total;
        }
        return x;
    }

    public void drawPage(DocCanvas c, int index) {
        List<int[]> p = paginate(c);
        int[] range = p.get(index);
        boolean first = index == 0;
        boolean last = index == p.size() - 1;
        float width = pageW - 2 * MARGIN;
        float y = MARGIN;
        DocFont titleFont = DocFont.bold(15);
        c.text(table.title(), MARGIN, y + 15, titleFont, INK);
        c.text(ReceiptLayout.fit(c, table.subtitle(), DocFont.regular(9), width), MARGIN, y + 31, DocFont.regular(9), MUTED);
        y += 44;
        if (first && !table.summaryLines().isEmpty()) {
            for (String[] s : table.summaryLines()) {
                y += 13;
                c.text(s[0] + ":", MARGIN, y, body, MUTED);
                c.text(s[1], MARGIN + 160, y, bold, INK);
            }
            y += 8;
        }
        y += 14;
        float[] x = columnX(width);
        c.fillRect(MARGIN, y, width, 20, BAND);
        for (int i = 0; i < table.columns().size(); i++) {
            ReportTable.Column col = table.columns().get(i);
            String name = ReceiptLayout.fit(c, col.name().toUpperCase(), head, x[i + 1] - x[i] - 8);
            cell(c, name, col.type().numeric(), x[i], x[i + 1], y + 13.5f, head, MUTED);
        }
        y += 20;
        float rowH = rowH(c);
        for (int r = range[0]; r < range[1]; r++) {
            Object[] row = table.rows().get(r);
            y += rowH;
            drawRow(c, row, x, y - 6, body, INK);
            c.line(MARGIN, y, MARGIN + width, y, 0.4f, RULE);
        }
        if (table.rows().isEmpty()) {
            y += rowH;
            c.text("No records for this period.", MARGIN + 4, y - 6, body, MUTED);
        }
        if (last && table.totals() != null) {
            y += rowH + 4;
            c.fillRect(MARGIN, y - rowH + 1, width, rowH, BAND);
            drawRow(c, table.totals(), x, y - 6, bold, INK);
        }
        DocFont tiny = DocFont.regular(7.5f);
        c.text("Smart Billing Pro  •  Printed " + Dates.formatDateTime(Dates.now()), MARGIN, pageH - MARGIN + 16, tiny, MUTED);
        String pg = "Page " + (index + 1) + " of " + p.size();
        c.text(pg, pageW - MARGIN - c.textWidth(pg, tiny), pageH - MARGIN + 16, tiny, MUTED);
    }

    private void drawRow(DocCanvas c, Object[] row, float[] x, float baseline, DocFont f, Color color) {
        for (int i = 0; i < table.columns().size() && i < row.length; i++) {
            ReportTable.Column col = table.columns().get(i);
            String text = ReportTable.format(row[i], col.type());
            text = ReceiptLayout.fit(c, text, f, x[i + 1] - x[i] - 8);
            Color cc = color;
            if (col.type() == ReportTable.Type.MONEY && row[i] instanceof Number n && n.longValue() < 0) {
                cc = new Color(0xB4, 0x23, 0x18);
            }
            cell(c, text, col.type().numeric(), x[i], x[i + 1], baseline, f, cc);
        }
    }

    private static void cell(DocCanvas c, String text, boolean rightAlign, float x1, float x2, float baseline, DocFont f,
                             Color color) {
        if (rightAlign) {
            c.text(text, x2 - 4 - c.textWidth(text, f), baseline, f, color);
        } else {
            c.text(text, x1 + 4, baseline, f, color);
        }
    }
}
