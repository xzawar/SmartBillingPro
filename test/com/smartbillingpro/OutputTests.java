package com.smartbillingpro;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.print.DocCanvas;
import com.smartbillingpro.print.DocFont;
import com.smartbillingpro.print.EscPosReceipt;
import com.smartbillingpro.print.Exporter;
import com.smartbillingpro.print.Graphics2DCanvas;
import com.smartbillingpro.print.ReceiptData;
import com.smartbillingpro.print.ReceiptLayout;
import com.smartbillingpro.service.Cart;
import com.smartbillingpro.service.ReportService;
import com.smartbillingpro.service.ReportTable;
import com.smartbillingpro.service.SaleService;
import com.smartbillingpro.util.Qty;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static com.smartbillingpro.TestSupport.check;
import static com.smartbillingpro.TestSupport.eq;
import static com.smartbillingpro.TestSupport.money;

/** Receipts (thermal + PDF), report PDFs and CSV exports. */
final class OutputTests {

    private OutputTests() {
    }

    /** Records the right-most x any text reaches, to prove nothing overflows the paper. */
    private static final class BoundsCanvas implements DocCanvas {
        private final DocCanvas metrics = Graphics2DCanvas.metricsOnly();
        float maxRight;
        float minLeft = Float.MAX_VALUE;
        int texts;

        @Override
        public float textWidth(String text, DocFont font) {
            return metrics.textWidth(text, font);
        }

        @Override
        public float ascent(DocFont font) {
            return metrics.ascent(font);
        }

        @Override
        public float lineHeight(DocFont font) {
            return metrics.lineHeight(font);
        }

        @Override
        public void text(String text, float x, float baselineY, DocFont font, Color color) {
            texts++;
            maxRight = Math.max(maxRight, x + textWidth(text, font));
            minLeft = Math.min(minLeft, x);
        }

        @Override
        public void line(float x1, float y1, float x2, float y2, float width, Color color) {
        }

        @Override
        public void fillRect(float x, float y, float w, float h, Color color) {
        }
    }

    private static Sale sampleSale(AppContext ctx) {
        ctx.settings().save(Map.of("store.name", "Al-Madina Super Store & General Traders",
                "store.address", "Shop 12, Main Bazaar Road, Model Town, Lahore", "store.phone", "0300-1234567",
                "receipt.footer", "No returns without receipt. Thank you for shopping with us!"));
        long a = TestSupport.simpleProduct(ctx, "Basmati Rice Super Kernel Premium Quality Extra Long Grain",
                "R1", "300", "500", "50", "kg");
        long b = TestSupport.simpleProduct(ctx, "Soap", "S1", "60", "100", "50", "pcs");
        long cid = ctx.parties().saveCustomer(null, "Muhammad Ahmad Khan", "0321-7654321", null, null, 0, true);
        Cart cart = new Cart();
        cart.setCustomer(ctx.parties().customer(cid));
        cart.add(ctx.inventory().sellable(a), Qty.parse("2.375"));
        Cart.Line soap = cart.add(ctx.inventory().sellable(b), Qty.of(12));
        cart.setLineDiscount(soap, money("50"));
        return ctx.sales().complete(cart, new SaleService.PaymentInput(TestSupport.cashMethod(ctx), money("2000"), null));
    }

    static void testThermalReceiptFitsPaper() throws Exception {
        try (AppContext ctx = TestSupport.newContext(TestSupport.tempDir())) {
            Sale sale = sampleSale(ctx);
            ReceiptData data = ReceiptData.of(sale, ctx.settings().get());
            for (int[] widths : new int[][] {{58, 48}, {80, 72}, {48, 40}}) {
                float printable = widths[1] * ReceiptLayout.MM;
                BoundsCanvas bc = new BoundsCanvas();
                float h = ReceiptLayout.drawRoll(bc, data, 0, 0, printable, widths[0] <= 58 ? 7 : 8);
                check(h > 100, "receipt has height");
                check(bc.texts > 20, "receipt has text");
                check(bc.maxRight <= printable + 0.5f, widths[0] + "mm: text reaches " + bc.maxRight + " > " + printable);
                check(bc.minLeft >= -0.01f, "nothing left of the margin");
            }
            for (int chars : new int[] {32, 42, 48}) {
                for (String line : EscPosReceipt.lines(data, chars)) {
                    check(line.length() <= chars, chars + " chars: line too long: [" + line + "]");
                }
            }
            byte[] bytes = EscPosReceipt.bytes(data, 32, true, true);
            check(bytes[0] == 0x1B && bytes[1] == '@', "ESC/POS init");
            check(new String(bytes, StandardCharsets.ISO_8859_1).contains(sale.receiptNo()), "receipt number in raw output");
        }
    }

    static void testReceiptPdfs() throws Exception {
        Path dir = TestSupport.tempDir();
        try (AppContext ctx = TestSupport.newContext(dir)) {
            Sale sale = sampleSale(ctx);
            ReceiptData data = ReceiptData.of(sale, ctx.settings().get());
            Path a4 = dir.resolve("receipt-a4.pdf");
            Exporter.receiptPdfA4(data, a4);
            Path roll = dir.resolve("receipt-80.pdf");
            Exporter.receiptPdfRoll(data, roll, 80, 72, 8);
            Path roll58 = dir.resolve("receipt-58.pdf");
            Exporter.receiptPdfRoll(data, roll58, 58, 48, 7);
            for (Path p : new Path[] {a4, roll, roll58}) {
                try (PDDocument doc = Loader.loadPDF(p.toFile())) {
                    eq(1, doc.getNumberOfPages(), p.getFileName() + " pages");
                    String text = new PDFTextStripper().getText(doc);
                    check(text.contains(sale.receiptNo()), p.getFileName() + " contains receipt number");
                    check(text.contains("Muhammad Ahmad Khan"), p.getFileName() + " contains customer");
                    check(text.contains("2,337.50"), p.getFileName() + " contains grand total");
                }
            }
            try (PDDocument doc = Loader.loadPDF(roll58.toFile())) {
                float w = doc.getPage(0).getMediaBox().getWidth();
                check(Math.abs(w - 58 * ReceiptLayout.MM) < 0.5, "58mm wide PDF");
            }
        }
    }

    static void testLongReceiptPaginates() throws Exception {
        Path dir = TestSupport.tempDir();
        try (AppContext ctx = TestSupport.newContext(dir)) {
            Cart cart = new Cart();
            for (int i = 0; i < 70; i++) {
                long v = TestSupport.simpleProduct(ctx, "Item number " + i, "B" + i, "1", "2", "5", "pcs");
                cart.add(ctx.inventory().sellable(v), Qty.of(1));
            }
            Sale s = ctx.sales().complete(cart, new SaleService.PaymentInput(TestSupport.cashMethod(ctx), money("140"), null));
            Path pdf = dir.resolve("long.pdf");
            Exporter.receiptPdfA4(ReceiptData.of(s, ctx.settings().get()), pdf);
            try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
                check(doc.getNumberOfPages() >= 2, "70 lines need more than one page");
                String text = new PDFTextStripper().getText(doc);
                check(text.contains("Item number 0") && text.contains("Item number 69"), "first and last item present");
            }
        }
    }

    static void testReportExports() throws Exception {
        Path dir = TestSupport.tempDir();
        try (AppContext ctx = TestSupport.newContext(dir)) {
            sampleSale(ctx);
            LocalDate today = LocalDate.now();
            for (ReportService.Definition d : ReportService.CATALOG) {
                ReportTable t = ctx.reports().run(d.id(), today.minusDays(30), today);
                Path pdf = dir.resolve(d.id() + ".pdf");
                Exporter.reportPdf(t, pdf);
                try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
                    check(doc.getNumberOfPages() >= 1, d.id() + " pdf has pages");
                    check(new PDFTextStripper().getText(doc).contains(t.title()), d.id() + " pdf has title");
                }
                Path csv = dir.resolve(d.id() + ".csv");
                Exporter.reportCsv(t, csv);
                String content = Files.readString(csv, StandardCharsets.UTF_8);
                check(content.startsWith("﻿" + t.title()), d.id() + " csv header");
            }
            ReportTable reg = ctx.reports().run("sales.register", today, today);
            Path csv = dir.resolve("reg.csv");
            Exporter.reportCsv(reg, csv);
            String content = Files.readString(csv, StandardCharsets.UTF_8);
            check(content.contains("2337.50"), "csv uses plain numbers");
        }
    }
}
