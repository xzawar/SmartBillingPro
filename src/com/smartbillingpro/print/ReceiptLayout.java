package com.smartbillingpro.print;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a receipt in two styles:
 * <ul>
 *   <li><b>Roll</b> - a narrow thermal receipt for any printable width (48, 58, 72, 80 mm...).</li>
 *   <li><b>Page</b> - a full A4 invoice for PDF export, paginated when needed.</li>
 * </ul>
 */
public final class ReceiptLayout {

    public static final float MM = 72f / 25.4f;
    private static final Color INK = Color.BLACK;
    private static final Color MUTED = new Color(0x55, 0x5B, 0x66);
    private static final Color RULE = new Color(0xC9, 0xCE, 0xD6);
    private static final Color BAND = new Color(0xF1, 0xF4, 0xF8);

    private ReceiptLayout() {
    }

    // ------------------------------------------------------------------ text helpers

    /** Word-wrap {@code text} so each line fits {@code width}; long words are broken. */
    public static List<String> wrap(DocCanvas c, String text, DocFont f, float width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String paragraph : text.split("\\r?\\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (c.textWidth(candidate, f) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                String w = word;
                while (c.textWidth(w, f) > width && w.length() > 1) {
                    int cut = w.length() - 1;
                    while (cut > 1 && c.textWidth(w.substring(0, cut), f) > width) {
                        cut--;
                    }
                    out.add(w.substring(0, cut));
                    w = w.substring(cut);
                }
                line.append(w);
            }
            if (line.length() > 0) {
                out.add(line.toString());
            }
        }
        return out;
    }

    /** Shorten text with an ellipsis so it fits {@code width}. */
    public static String fit(DocCanvas c, String text, DocFont f, float width) {
        if (text == null) {
            return "";
        }
        if (c.textWidth(text, f) <= width) {
            return text;
        }
        String s = text;
        while (s.length() > 1 && c.textWidth(s + "…", f) > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private static void center(DocCanvas c, String s, float y, DocFont f, float x0, float width, Color color) {
        c.text(s, x0 + (width - c.textWidth(s, f)) / 2f, y, f, color);
    }

    private static void right(DocCanvas c, String s, float rightX, float y, DocFont f, Color color) {
        c.text(s, rightX - c.textWidth(s, f), y, f, color);
    }

    // ------------------------------------------------------------------ roll (thermal)

    /**
     * Draw a thermal receipt starting at the top-left (x0, y0) with the given
     * printable width (points). Returns the total height used.
     */
    public static float drawRoll(DocCanvas c, ReceiptData d, float x0, float y0, float width, float baseSize) {
        DocFont body = DocFont.regular(baseSize);
        DocFont bold = DocFont.bold(baseSize);
        DocFont title = DocFont.bold(baseSize + 4);
        DocFont total = DocFont.bold(baseSize + 3);
        DocFont small = DocFont.regular(Math.max(5.5f, baseSize - 1));
        float lh = c.lineHeight(body);
        float y = y0;

        for (String s : wrap(c, d.storeName(), title, width)) {
            y += c.lineHeight(title);
            center(c, s, y - c.lineHeight(title) * 0.25f, title, x0, width, INK);
        }
        for (String line : d.storeLines()) {
            for (String s : wrap(c, line, small, width)) {
                y += c.lineHeight(small);
                center(c, s, y - 2, small, x0, width, INK);
            }
        }
        if (!d.header().isEmpty()) {
            y += lh * 0.3f;
            for (String s : wrap(c, d.header(), body, width)) {
                y += lh;
                center(c, s, y - 2, body, x0, width, INK);
            }
        }
        y += lh * 0.5f;
        c.dashed(x0, y, x0 + width, INK);
        y += lh * 0.3f;

        y = pair(c, "Receipt", d.receiptNo(), x0, y, width, body, bold);
        y = pair(c, "Date", d.dateTime(), x0, y, width, body, body);
        y = pair(c, "Customer", d.customerName(), x0, y, width, body, body);
        if (d.customerPhone() != null && !d.customerPhone().isBlank()) {
            y = pair(c, "Phone", d.customerPhone(), x0, y, width, body, body);
        }
        if (d.statusNote() != null) {
            y += lh;
            center(c, d.statusNote(), y - 2, bold, x0, width, INK);
        }
        y += lh * 0.4f;
        c.dashed(x0, y, x0 + width, INK);
        y += lh * 0.2f;

        for (ReceiptData.Line l : d.lines()) {
            for (String s : wrap(c, l.name(), bold, width)) {
                y += lh;
                c.text(s, x0, y - 2, bold, INK);
            }
            y += lh;
            String left = l.qtyText() + " x " + l.unitPrice();
            c.text(fit(c, left, body, width * 0.62f), x0 + 4, y - 2, body, INK);
            right(c, l.amount(), x0 + width, y - 2, body, INK);
            if (l.discount() != null) {
                y += lh;
                c.text("Discount", x0 + 4, y - 2, small, INK);
                right(c, l.discount(), x0 + width, y - 2, body, INK);
            }
        }
        y += lh * 0.4f;
        c.dashed(x0, y, x0 + width, INK);
        y += lh * 0.2f;
        for (ReceiptData.Total t : d.totals()) {
            y = pair(c, t.label(), t.value(), x0, y, width, body, body);
        }
        y += c.lineHeight(total) + 1;
        c.text("TOTAL", x0, y - 3, total, INK);
        right(c, d.grandTotal(), x0 + width, y - 3, total, INK);
        y += 2;
        for (ReceiptData.Total t : d.payment()) {
            y = pair(c, t.label(), t.value(), x0, y, width, t.strong() ? bold : body, t.strong() ? bold : body);
        }
        y += lh * 0.4f;
        c.dashed(x0, y, x0 + width, INK);
        if (!d.footer().isEmpty()) {
            y += lh * 0.3f;
            for (String s : wrap(c, d.footer(), body, width)) {
                y += lh;
                center(c, s, y - 2, body, x0, width, INK);
            }
        }
        y += lh;
        center(c, d.currencyNote(), y - 2, small, x0, width, INK);
        y += lh * 0.8f;
        return y - y0;
    }

    private static float pair(DocCanvas c, String label, String value, float x0, float y, float width, DocFont lf,
                              DocFont vf) {
        float lh = c.lineHeight(lf);
        String v = value == null ? "" : value;
        float vw = Math.min(c.textWidth(v, vf), width * 0.68f);
        y += lh;
        c.text(fit(c, label, lf, width - vw - 6), x0, y - 2, lf, INK);
        right(c, fit(c, v, vf, width * 0.68f), x0 + width, y - 2, vf, INK);
        return y;
    }

    /** Height a roll receipt needs, measured without drawing. */
    public static float measureRoll(ReceiptData d, float width, float baseSize, DocCanvas metrics) {
        return drawRoll(new MeasureCanvas(metrics), d, 0, 0, width, baseSize);
    }

    // ------------------------------------------------------------------ page (A4)

    private static final float PAGE_W = 595.28f;
    private static final float PAGE_H = 841.89f;
    private static final float MARGIN = 42f;

    /** Number of A4 pages the receipt needs. */
    public static int pageCount(DocCanvas metrics, ReceiptData d) {
        return paginate(metrics, d).size();
    }

    private static List<int[]> paginate(DocCanvas c, ReceiptData d) {
        float rowH = c.lineHeight(DocFont.regular(9.5f)) + 7;
        int n = d.lines().size();
        List<int[]> pages = new ArrayList<>();
        int i = 0;
        boolean first = true;
        while (true) {
            float top = first ? 250 : 120;
            int capFull = Math.max(1, (int) ((PAGE_H - MARGIN - 40 - top) / rowH));
            int capLast = Math.max(0, (int) ((PAGE_H - MARGIN - 230 - top) / rowH));
            int remaining = n - i;
            if (remaining <= capLast) {
                pages.add(new int[] {i, n});
                return pages;
            }
            int take = Math.min(capFull, remaining);
            pages.add(new int[] {i, i + take});
            i += take;
            first = false;
        }
    }

    /** Draw page {@code index} of the A4 receipt. The canvas must already be on an A4 page. */
    public static void drawPage(DocCanvas c, ReceiptData d, int index) {
        List<int[]> pages = paginate(c, d);
        int[] range = pages.get(index);
        boolean first = index == 0;
        boolean last = index == pages.size() - 1;
        float w = PAGE_W - 2 * MARGIN;
        float x0 = MARGIN;
        DocFont body = DocFont.regular(9.5f);
        DocFont bodyBold = DocFont.bold(9.5f);
        DocFont small = DocFont.regular(8.5f);
        DocFont label = DocFont.bold(7.5f);
        float y = MARGIN;

        if (first) {
            DocFont name = DocFont.bold(20);
            float ty = y + 20;
            c.text(fit(c, d.storeName(), name, w * 0.6f), x0, ty, name, INK);
            float ly = ty + 6;
            for (String line : d.storeLines()) {
                for (String s : wrap(c, line, small, w * 0.6f)) {
                    ly += c.lineHeight(small);
                    c.text(s, x0, ly, small, MUTED);
                }
            }
            DocFont big = DocFont.bold(16);
            right(c, "RECEIPT", x0 + w, ty, big, INK);
            float ry = ty + 4;
            ry = metaRight(c, "Receipt no.", d.receiptNo(), x0 + w, ry);
            ry = metaRight(c, "Date", d.dateTime(), x0 + w, ry);
            metaRight(c, "Payment", d.paymentMethod(), x0 + w, ry);
            y = Math.max(ly, ry) + 24;
            c.line(x0, y, x0 + w, y, 0.8f, RULE);
            y += 18;
            c.text("BILL TO", x0, y, label, MUTED);
            y += c.lineHeight(bodyBold) + 2;
            c.text(d.customerName(), x0, y, bodyBold, INK);
            if (d.customerPhone() != null && !d.customerPhone().isBlank()) {
                y += c.lineHeight(body);
                c.text(d.customerPhone(), x0, y, body, MUTED);
            }
            if (d.statusNote() != null) {
                right(c, d.statusNote(), x0 + w, y, bodyBold, new Color(0xB4, 0x23, 0x18));
            }
            y = 250 - 30;
        } else {
            DocFont h = DocFont.bold(11);
            c.text(d.storeName() + "  •  " + d.receiptNo() + " (continued)", x0, y + 14, h, INK);
            y = 120 - 30;
        }

        // table header
        float[] cols = {x0 + 8, x0 + 28, x0 + w * 0.58f, x0 + w * 0.72f, x0 + w * 0.86f, x0 + w - 8};
        c.fillRect(x0, y, w, 22, BAND);
        float hy = y + 14.5f;
        c.text("#", cols[0], hy, label, MUTED);
        c.text("ITEM", cols[1], hy, label, MUTED);
        right(c, "QTY", cols[3] - 10, hy, label, MUTED);
        right(c, "UNIT PRICE", cols[4] - 10, hy, label, MUTED);
        right(c, "AMOUNT", cols[5], hy, label, MUTED);
        y += 22;
        float rowH = c.lineHeight(body) + 7;
        for (int i = range[0]; i < range[1]; i++) {
            ReceiptData.Line l = d.lines().get(i);
            float by = y + rowH - 6;
            c.text(String.valueOf(i + 1), cols[0], by, body, MUTED);
            String name = l.discount() == null ? l.name() : l.name() + "  (disc. " + l.discount() + ")";
            c.text(fit(c, name, body, cols[2] - cols[1] + w * 0.04f), cols[1], by, body, INK);
            right(c, l.qtyText(), cols[3] - 10, by, body, INK);
            right(c, l.unitPrice(), cols[4] - 10, by, body, INK);
            right(c, l.amount(), cols[5], by, body, INK);
            y += rowH;
            c.line(x0, y, x0 + w, y, 0.4f, RULE);
        }

        if (last) {
            y += 16;
            float lx = x0 + w * 0.55f;
            for (ReceiptData.Total t : d.totals()) {
                y += c.lineHeight(body) + 3;
                c.text(t.label(), lx, y, body, MUTED);
                right(c, t.value(), x0 + w - 8, y, body, INK);
            }
            y += 10;
            c.fillRect(lx - 8, y, x0 + w - lx + 8, 28, BAND);
            DocFont tot = DocFont.bold(13);
            c.text("Total", lx, y + 19, tot, INK);
            right(c, d.grandTotal(), x0 + w - 8, y + 19, tot, INK);
            y += 28;
            for (ReceiptData.Total t : d.payment()) {
                y += c.lineHeight(body) + 3;
                DocFont f = t.strong() ? bodyBold : body;
                c.text(t.label(), lx, y, f, t.strong() ? INK : MUTED);
                right(c, t.value(), x0 + w - 8, y, f, INK);
            }
            if (!d.footer().isEmpty()) {
                float fy = PAGE_H - MARGIN - 30;
                for (String s : wrap(c, d.footer(), body, w)) {
                    center(c, s, fy, body, x0, w, MUTED);
                    fy += c.lineHeight(body);
                }
            }
        }
        DocFont tiny = DocFont.regular(7.5f);
        right(c, d.currencyNote() + "   •   Page " + (index + 1) + " of " + pages.size(), x0 + w,
                PAGE_H - MARGIN + 14, tiny, MUTED);
    }

    private static float metaRight(DocCanvas c, String label, String value, float rightX, float y) {
        DocFont l = DocFont.regular(8.5f);
        DocFont v = DocFont.bold(9.5f);
        y += 15;
        String text = value == null ? "" : value;
        right(c, text, rightX, y, v, INK);
        right(c, label + "   ", rightX - c.textWidth(text, v), y, l, MUTED);
        return y;
    }

    public static float pageWidth() {
        return PAGE_W;
    }

    public static float pageHeight() {
        return PAGE_H;
    }
}
