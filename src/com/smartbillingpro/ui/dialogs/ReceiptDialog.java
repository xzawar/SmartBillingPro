package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.print.Exporter;
import com.smartbillingpro.print.Graphics2DCanvas;
import com.smartbillingpro.print.Printers;
import com.smartbillingpro.print.ReceiptData;
import com.smartbillingpro.print.ReceiptLayout;
import com.smartbillingpro.service.Settings;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** Receipt preview with Print and Save as PDF. */
public final class ReceiptDialog extends FormDialog {

    private final AppContext ctx;
    private final Sale sale;

    public ReceiptDialog(Component parent, AppContext ctx, Sale sale) {
        super(parent, "Receipt " + sale.receiptNo(), "Print receipt");
        this.ctx = ctx;
        this.sale = sale;
        cancelButton.setText("Close");
        JButton pdf = Ui.secondary("Save as PDF", "pdf");
        pdf.addActionListener(e -> savePdfAs());
        addFooterLeft(pdf);
        JPanel body = new JPanel(new BorderLayout());
        body.add(new Preview(ReceiptData.of(sale, ctx.settings().get()), ctx.settings().get()), BorderLayout.CENTER);
        setBody(body);
    }

    @Override
    protected boolean onSave() {
        print(ctx, sale);
        Toast.ok(getOwner(), "Receipt sent to the printer.");
        return true;
    }

    public static void print(AppContext ctx, Sale sale) {
        Printers.printReceipt(ReceiptData.of(sale, ctx.settings().get()), ctx.settings().get());
    }

    public static void savePdf(AppContext ctx, Sale sale, Path file) throws IOException {
        Settings st = ctx.settings().get();
        ReceiptData data = ReceiptData.of(sale, st);
        if ("THERMAL".equals(st.pdfSize())) {
            Exporter.receiptPdfRoll(data, file, st.paperWidthMm(), st.printableWidthMm(), st.receiptFontPt());
        } else {
            Exporter.receiptPdfA4(data, file);
        }
    }

    private void savePdfAs() {
        JFileChooser fc = new JFileChooser(ctx.paths().defaultExportDir().toFile());
        fc.setSelectedFile(new File(ctx.paths().defaultExportDir().toFile(), Exporter.safeFileName(sale.receiptNo()) + ".pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".pdf")) {
            f = new File(f.getParentFile(), f.getName() + ".pdf");
        }
        try {
            savePdf(ctx, sale, f.toPath());
            Toast.ok(this, "Saved " + f.getName());
        } catch (IOException | RuntimeException e) {
            Msg.error(this, "The PDF could not be saved: " + e.getMessage());
        }
    }

    /** Paper-like on-screen rendering of the thermal receipt. */
    public static final class Preview extends JComponent {
        private final ReceiptData data;
        private final float printable;
        private final float font;
        private final float height;
        private static final float SCALE = 1.45f;

        public Preview(ReceiptData data, Settings st) {
            this.data = data;
            this.printable = st.printableWidthMm() * ReceiptLayout.MM;
            this.font = st.receiptFontPt();
            this.height = ReceiptLayout.measureRoll(data, printable, font, Graphics2DCanvas.metricsOnly());
            setPreferredSize(new Dimension((int) ((printable + 24) * SCALE) + 20, (int) ((height + 24) * SCALE) + 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Theme.BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            int pw = (int) ((printable + 24) * SCALE);
            int x = Math.max(10, (getWidth() - pw) / 2);
            g2.setColor(new java.awt.Color(0, 0, 0, 25));
            g2.fillRect(x + 3, 13, pw, (int) ((height + 24) * SCALE));
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(x, 10, pw, (int) ((height + 24) * SCALE));
            g2.translate(x, 10);
            g2.scale(SCALE, SCALE);
            ReceiptLayout.drawRoll(new Graphics2DCanvas(g2), data, 12, 12, printable, font);
            g2.dispose();
        }
    }
}
