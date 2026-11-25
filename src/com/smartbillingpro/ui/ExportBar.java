package com.smartbillingpro.ui;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.print.Exporter;
import com.smartbillingpro.print.Printers;
import com.smartbillingpro.service.ReportTable;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.function.Supplier;

/** PDF / Excel (CSV) / Print buttons for any report-shaped data. */
public final class ExportBar {

    private ExportBar() {
    }

    public static JPanel create(AppContext ctx, Component owner, Supplier<ReportTable> source) {
        JButton pdf = Ui.secondary("PDF", "pdf");
        pdf.setToolTipText("Save as PDF");
        pdf.addActionListener(e -> export(ctx, owner, source, true));
        JButton csv = Ui.secondary("Excel / CSV", "download");
        csv.setToolTipText("Save as a CSV file that opens in Excel");
        csv.addActionListener(e -> export(ctx, owner, source, false));
        JButton print = Ui.secondary("Print", "print");
        print.addActionListener(e -> {
            try {
                ReportTable t = source.get();
                if (t != null && Printers.printReport(t)) {
                    Toast.ok(owner, "Sent to the printer.");
                }
            } catch (RuntimeException ex) {
                Msg.error(owner, ex);
            }
        });
        return Ui.flowRight(6, pdf, csv, print);
    }

    private static void export(AppContext ctx, Component owner, Supplier<ReportTable> source, boolean pdf) {
        ReportTable t;
        try {
            t = source.get();
        } catch (RuntimeException e) {
            Msg.error(owner, e);
            return;
        }
        if (t == null) {
            return;
        }
        File dir = ctx.paths().defaultExportDir().toFile();
        dir.mkdirs();
        JFileChooser fc = new JFileChooser(dir);
        String ext = pdf ? "pdf" : "csv";
        fc.setFileFilter(new FileNameExtensionFilter(pdf ? "PDF document" : "CSV (opens in Excel)", ext));
        fc.setSelectedFile(new File(dir, Exporter.safeFileName(t.title() + " " + LocalDate.now()) + "." + ext));
        if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith("." + ext)) {
            f = new File(f.getParentFile(), f.getName() + "." + ext);
        }
        try {
            if (pdf) {
                Exporter.reportPdf(t, f.toPath());
            } else {
                Exporter.reportCsv(t, f.toPath());
            }
            final File saved = f;
            Toast.ok(owner, "Saved " + f.getName());
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
                    && Msg.confirm(owner, "Saved", "Saved to " + saved.getAbsolutePath() + "\n\nOpen it now?", "Open")) {
                Desktop.getDesktop().open(saved);
            }
        } catch (IOException | RuntimeException e) {
            Msg.error(owner, "The file could not be saved: " + e.getMessage());
        }
    }
}
