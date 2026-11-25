package com.smartbillingpro.print;

import com.smartbillingpro.service.ReportTable;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes receipts and reports to PDF and CSV files. Works fully offline. */
public final class Exporter {

    private Exporter() {
    }

    /** A4 invoice-style PDF receipt. */
    public static void receiptPdfA4(ReceiptData data, Path file) throws IOException {
        try (PdfCanvas pdf = new PdfCanvas()) {
            int pages = ReceiptLayout.pageCount(pdf, data);
            for (int i = 0; i < pages; i++) {
                pdf.newPage(ReceiptLayout.pageWidth(), ReceiptLayout.pageHeight());
                ReceiptLayout.drawPage(pdf, data, i);
            }
            pdf.save(file);
        }
    }

    /** Narrow PDF exactly as the thermal receipt prints (paper width in mm). */
    public static void receiptPdfRoll(ReceiptData data, Path file, int paperMm, int printableMm, float fontPt)
            throws IOException {
        try (PdfCanvas pdf = new PdfCanvas()) {
            float paper = paperMm * ReceiptLayout.MM;
            float printable = printableMm * ReceiptLayout.MM;
            float margin = (paper - printable) / 2f;
            float height = ReceiptLayout.measureRoll(data, printable, fontPt, pdf) + 2 * 8;
            pdf.newPage(paper, height);
            ReceiptLayout.drawRoll(pdf, data, margin, 8, printable, fontPt);
            pdf.save(file);
        }
    }

    public static void reportPdf(ReportTable table, Path file) throws IOException {
        boolean landscape = TableLayout.prefersLandscape(table);
        float w = landscape ? ReceiptLayout.pageHeight() : ReceiptLayout.pageWidth();
        float h = landscape ? ReceiptLayout.pageWidth() : ReceiptLayout.pageHeight();
        try (PdfCanvas pdf = new PdfCanvas()) {
            TableLayout layout = new TableLayout(table, w, h);
            int pages = layout.pageCount(pdf);
            for (int i = 0; i < pages; i++) {
                pdf.newPage(w, h);
                layout.drawPage(pdf, i);
            }
            pdf.save(file);
        }
    }

    /** CSV with a UTF-8 BOM so Excel opens it with the right characters. */
    public static void reportCsv(ReportTable table, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            w.write('﻿');
            w.write(csv(table.title()) + "\r\n");
            w.write(csv(table.subtitle()) + "\r\n");
            for (String[] s : table.summaryLines()) {
                w.write(csv(s[0]) + "," + csv(s[1]) + "\r\n");
            }
            w.write("\r\n");
            StringBuilder head = new StringBuilder();
            for (int i = 0; i < table.columns().size(); i++) {
                head.append(i == 0 ? "" : ",").append(csv(table.columns().get(i).name()));
            }
            w.write(head + "\r\n");
            for (Object[] row : table.rows()) {
                w.write(csvRow(table, row) + "\r\n");
            }
            if (table.totals() != null) {
                w.write(csvRow(table, table.totals()) + "\r\n");
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String csvRow(ReportTable t, Object[] row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.columns().size(); i++) {
            Object v = i < row.length ? row[i] : null;
            sb.append(i == 0 ? "" : ",").append(csv(ReportTable.csvValue(v, t.columns().get(i).type())));
        }
        return sb.toString();
    }

    static String csv(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        // Neutralise spreadsheet formulas in user-entered text.
        if (!v.isEmpty() && "=+@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** A file name made safe for Windows. */
    public static String safeFileName(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]+", "-").replaceAll("\\s+", "_");
    }
}
