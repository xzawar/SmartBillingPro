package com.smartbillingpro.print;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.service.ReportTable;
import com.smartbillingpro.service.Settings;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaPrintableArea;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends receipts and reports to printers installed in Windows (USB thermal
 * printers included). No network or cloud printing is involved.
 */
public final class Printers {

    private Printers() {
    }

    public static List<String> installedPrinters() {
        List<String> names = new ArrayList<>();
        for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
            names.add(s.getName());
        }
        return names;
    }

    /** The configured receipt printer, else the Windows default printer. */
    public static PrintService receiptPrinter(Settings st) {
        String name = st.printerName();
        if (name != null && !name.isBlank()) {
            for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null)) {
                if (s.getName().equalsIgnoreCase(name.trim())) {
                    return s;
                }
            }
            throw new AppException("The receipt printer \"" + name + "\" was not found. Check that it is switched on "
                    + "and connected, or choose another printer in Tools & Settings.");
        }
        PrintService def = PrintServiceLookup.lookupDefaultPrintService();
        if (def == null) {
            throw new AppException("No printer is installed. Install your receipt printer in Windows, then select it "
                    + "in Tools & Settings > Printer.");
        }
        return def;
    }

    /** Print a receipt on the thermal printer using the configured mode. */
    public static void printReceipt(ReceiptData data, Settings st) {
        PrintService service = receiptPrinter(st);
        if (st.printerMode() == Settings.PrinterMode.ESCPOS) {
            printRaw(service, EscPosReceipt.bytes(data, st.charsPerLine(), st.cutPaper(), st.openDrawer()),
                    "Receipt " + data.receiptNo());
        } else {
            printRollViaDriver(service, data, st.paperWidthMm(), st.printableWidthMm(), st.receiptFontPt());
        }
    }

    private static void printRaw(PrintService service, byte[] bytes, String jobName) {
        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        if (!service.isDocFlavorSupported(flavor)) {
            throw new AppException("The printer \"" + service.getName() + "\" does not accept raw ESC/POS data. "
                    + "Switch the printer mode to \"Windows printer driver\" in Tools & Settings.");
        }
        try {
            DocPrintJob job = service.createPrintJob();
            Doc doc = new SimpleDoc(bytes, flavor, null);
            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            attrs.add(new JobName(jobName, null));
            job.print(doc, attrs);
        } catch (PrintException e) {
            throw new AppException("Printing failed: " + e.getMessage(), e);
        }
    }

    private static void printRollViaDriver(PrintService service, ReceiptData data, int paperMm, int printableMm,
                                           float fontPt) {
        float paperW = paperMm * ReceiptLayout.MM;
        float printable = printableMm * ReceiptLayout.MM;
        float margin = (paperW - printable) / 2f;
        float height = ReceiptLayout.measureRoll(data, printable, fontPt, Graphics2DCanvas.metricsOnly()) + 24;
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(service);
            job.setJobName("Receipt " + data.receiptNo());
            PageFormat pf = job.defaultPage();
            Paper paper = new Paper();
            paper.setSize(paperW, height);
            paper.setImageableArea(0, 0, paperW, height);
            pf.setPaper(paper);
            pf.setOrientation(PageFormat.PORTRAIT);
            job.setPrintable((Graphics g, PageFormat format, int page) -> {
                if (page > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                Graphics2D g2 = (Graphics2D) g;
                ReceiptLayout.drawRoll(new Graphics2DCanvas(g2), data, margin, 6, printable, fontPt);
                return Printable.PAGE_EXISTS;
            }, pf);
            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            attrs.add(new MediaPrintableArea(0, 0, paperMm, Math.max(paperMm, height / ReceiptLayout.MM),
                    MediaPrintableArea.MM));
            job.print(attrs);
        } catch (PrinterException e) {
            throw new AppException("Printing failed: " + e.getMessage(), e);
        }
    }

    /** Print a report on an A4 printer chosen in the Windows print dialog. Returns false if cancelled. */
    public static boolean printReport(ReportTable table) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(table.title());
        PageFormat pf = job.defaultPage();
        Paper a4 = pf.getPaper();
        // Content keeps its own 0.5" margin; widen the printable area so nothing is clipped.
        a4.setImageableArea(18, 18, a4.getWidth() - 36, a4.getHeight() - 36);
        pf.setPaper(a4);
        boolean landscape = TableLayout.prefersLandscape(table);
        pf.setOrientation(landscape ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        Printable printable = new Printable() {
            private TableLayout layout;
            private int pages = -1;

            @Override
            public int print(Graphics g, PageFormat format, int page) {
                Graphics2D g2 = (Graphics2D) g;
                if (layout == null) {
                    layout = new TableLayout(table, (float) format.getWidth(), (float) format.getHeight());
                    pages = layout.pageCount(new Graphics2DCanvas(g2));
                }
                if (page >= pages) {
                    return Printable.NO_SUCH_PAGE;
                }
                layout.drawPage(new Graphics2DCanvas(g2), page);
                return Printable.PAGE_EXISTS;
            }
        };
        job.setPrintable(printable, pf);
        if (!job.printDialog()) {
            return false;
        }
        try {
            job.print();
            return true;
        } catch (PrinterException e) {
            throw new AppException("Printing failed: " + e.getMessage(), e);
        }
    }
}
