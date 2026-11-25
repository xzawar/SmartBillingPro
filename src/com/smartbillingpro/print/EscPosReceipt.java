package com.smartbillingpro.print;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain-text receipt for printers driven with raw ESC/POS commands. The text
 * width is the printer's characters-per-line (typically 32 for 58 mm paper and
 * 48 for 80 mm paper).
 */
public final class EscPosReceipt {

    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;

    private EscPosReceipt() {
    }

    /** The receipt as lines of exactly {@code width} characters or fewer. */
    public static List<String> lines(ReceiptData d, int width) {
        List<String> out = new ArrayList<>();
        for (String s : wrap(d.storeName(), width)) {
            out.add(center(s, width));
        }
        for (String line : d.storeLines()) {
            for (String s : wrap(line, width)) {
                out.add(center(s, width));
            }
        }
        if (!d.header().isEmpty()) {
            for (String s : wrap(d.header(), width)) {
                out.add(center(s, width));
            }
        }
        out.add("-".repeat(width));
        out.add(pair("Receipt", d.receiptNo(), width));
        out.add(pair("Date", d.dateTime(), width));
        out.add(pair("Customer", d.customerName(), width));
        if (d.customerPhone() != null && !d.customerPhone().isBlank()) {
            out.add(pair("Phone", d.customerPhone(), width));
        }
        if (d.statusNote() != null) {
            out.add(center(d.statusNote(), width));
        }
        out.add("-".repeat(width));
        for (ReceiptData.Line l : d.lines()) {
            out.addAll(wrap(l.name(), width));
            out.add(pair("  " + l.qtyText() + " x " + l.unitPrice(), l.amount(), width));
            if (l.discount() != null) {
                out.add(pair("  Discount", l.discount(), width));
            }
        }
        out.add("-".repeat(width));
        for (ReceiptData.Total t : d.totals()) {
            out.add(pair(t.label(), t.value(), width));
        }
        out.add("=".repeat(width));
        out.add(pair("TOTAL", d.grandTotal(), width));
        out.add("=".repeat(width));
        for (ReceiptData.Total t : d.payment()) {
            out.add(pair(t.label(), t.value(), width));
        }
        out.add("-".repeat(width));
        if (!d.footer().isEmpty()) {
            for (String s : wrap(d.footer(), width)) {
                out.add(center(s, width));
            }
        }
        out.add(center(d.currencyNote(), width));
        return out;
    }

    /** Printer bytes: initialise, text, feed, optional cut and cash drawer pulse. */
    public static byte[] bytes(ReceiptData d, int width, boolean cut, boolean drawer) {
        Charset cs = Charset.isSupported("IBM437") ? Charset.forName("IBM437") : Charset.forName("US-ASCII");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {ESC, '@'});
        if (drawer) {
            out.writeBytes(new byte[] {ESC, 'p', 0, 25, (byte) 250});
        }
        List<String> lines = lines(d, width);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean emphasis = i == 0 || line.startsWith("TOTAL");
            if (emphasis) {
                out.writeBytes(new byte[] {ESC, 'E', 1});
            }
            out.writeBytes(ascii(line).getBytes(cs));
            out.write('\n');
            if (emphasis) {
                out.writeBytes(new byte[] {ESC, 'E', 0});
            }
        }
        out.writeBytes(new byte[] {ESC, 'd', 4});
        if (cut) {
            out.writeBytes(new byte[] {GS, 'V', 66, 0});
        }
        return out.toByteArray();
    }

    private static String ascii(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '—' || c == '–' || c == '−') {
                sb.append('-');
            } else if (c == '•' || c == '·') {
                sb.append('*');
            } else if (c == '…') {
                sb.append("...");
            } else {
                sb.append(c < 0x7F || Character.isLetterOrDigit(c) ? c : '?');
            }
        }
        return sb.toString();
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            while (word.length() > width) {
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= width) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    static String center(String s, int width) {
        if (s.length() >= width) {
            return s.substring(0, width);
        }
        return " ".repeat((width - s.length()) / 2) + s;
    }

    static String pair(String label, String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() > width) {
            v = v.substring(0, width);
        }
        int room = width - v.length() - 1;
        String l = label.length() > room ? label.substring(0, Math.max(0, room)) : label;
        return l + " ".repeat(Math.max(1, width - l.length() - v.length())) + v;
    }
}
