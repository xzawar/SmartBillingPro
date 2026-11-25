package com.smartbillingpro.ui.pages;

import com.smartbillingpro.app.AppPaths;
import com.smartbillingpro.model.Dimension;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.model.Unit;
import com.smartbillingpro.print.Printers;
import com.smartbillingpro.print.ReceiptData;
import com.smartbillingpro.service.BackupService;
import com.smartbillingpro.service.LegacyImportService;
import com.smartbillingpro.service.Settings;
import com.smartbillingpro.ui.Card;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Page;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Toast;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.ui.dialogs.LegacyImportDialog;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Store details, receipts, printer, payment methods, units, backups and security. */
public final class SettingsPage extends Page {

    private final JTabbedPane tabs = new JTabbedPane();

    // store
    private final JTextField storeName = Ui.field();
    private final JTextField storeAddress = Ui.field();
    private final JTextField storePhone = Ui.field();
    private final JTextField storeEmail = Ui.field("optional");
    private final JTextField storeTaxId = Ui.field("optional, e.g. NTN / STRN");
    private final JTextField currencySymbol = Ui.field("Rs");
    private final JTextField currencyCode = Ui.field("PKR");
    private final JComboBox<String> dateFormat = new JComboBox<>(Dates.DISPLAY_PATTERNS);
    private final JTextField lowStock = Ui.field("5");

    // sales & receipts
    private final JCheckBox taxEnabled = new JCheckBox("Add tax to sales");
    private final JTextField taxPercent = Ui.field("0");
    private final JTextField taxLabel = Ui.field("GST");
    private final JTextField prefix = Ui.field("INV");
    private final JTextArea header = new JTextArea(2, 20);
    private final JTextArea footer = new JTextArea(2, 20);
    private final JComboBox<PaymentMethod> defaultMethod = new JComboBox<>();
    private final JComboBox<Settings.AfterSale> afterSale = new JComboBox<>(Settings.AfterSale.values());
    private final JCheckBox autoPdf = new JCheckBox("Also save a PDF copy of every receipt");
    private final JTextField pdfDir = Ui.field("Documents\\SmartBillingPro Exports\\Receipts");
    private final JComboBox<String> pdfSize = new JComboBox<>(new String[] {"A4 page", "Same width as thermal receipt"});

    // printer
    private final JComboBox<String> printer = new JComboBox<>();
    private final JComboBox<String> paper = new JComboBox<>(new String[] {"80 mm", "58 mm", "Custom"});
    private final JTextField paperMm = Ui.field("80");
    private final JTextField printableMm = Ui.field("72");
    private final JComboBox<Settings.PrinterMode> mode = new JComboBox<>(Settings.PrinterMode.values());
    private final JTextField chars = Ui.field("48");
    private final JTextField fontPt = Ui.field("8");
    private final JCheckBox cut = new JCheckBox("Cut paper after each receipt (ESC/POS)");
    private final JCheckBox drawer = new JCheckBox("Open cash drawer (ESC/POS)");

    // lists
    private final DataTable<PaymentMethod> methods = new DataTable<>();
    private final DataTable<Unit> units = new DataTable<>();

    // backup
    private final JLabel backupStatus = new JLabel(" ");
    private final JLabel backupFolder = Ui.muted(" ");
    private final JCheckBox autoBackup = new JCheckBox("Back up automatically every day");
    private final JSpinner keep = new JSpinner(new SpinnerNumberModel(30, 3, 3650, 1));
    private final DataTable<BackupService.BackupInfo> backups = new DataTable<>();

    // security
    private final JLabel pinState = Ui.muted(" ");

    public SettingsPage(MainFrame frame) {
        super(frame, "Tools & Settings", "Set up your shop once – everything is saved on this computer");
        tabs.addTab("Store", buildStore());
        tabs.addTab("Sales & receipts", buildReceipts());
        tabs.addTab("Printer", buildPrinter());
        tabs.addTab("Payments & units", buildLists());
        tabs.addTab("Backup & restore", buildBackup());
        tabs.addTab("Security & data", buildSecurity());
        tabs.addChangeListener(e -> refresh());
        setBody(tabs);
        for (JCheckBox c : new JCheckBox[] {taxEnabled, autoPdf, cut, drawer, autoBackup}) {
            c.setOpaque(false);
        }
    }

    public void showBackupTab() {
        tabs.setSelectedIndex(4);
    }

    public void showPrinterTab() {
        tabs.setSelectedIndex(2);
    }

    /** A settings form that scrolls, with its Save button always visible at the bottom. */
    private JComponent form(JComponent content, Runnable save) {
        Card c = new Card(new BorderLayout());
        c.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        c.add(content, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.BG);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(sp, BorderLayout.CENTER);
        if (save != null) {
            JButton b = Ui.primary("Save changes", "check");
            b.addActionListener(e -> safely(() -> {
                save.run();
                Toast.ok(this, "Settings saved.");
            }));
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            footer.add(Ui.muted("Changes are used straight away after saving."), BorderLayout.WEST);
            footer.add(b, BorderLayout.EAST);
            p.add(footer, BorderLayout.SOUTH);
        }
        return p;
    }

    private static JComponent area(JTextArea a) {
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        JScrollPane s = new JScrollPane(a);
        s.setPreferredSize(new java.awt.Dimension(200, 54));
        return s;
    }

    // ------------------------------------------------------------ store

    private JComponent buildStore() {
        com.smartbillingpro.ui.Collapsible more = new com.smartbillingpro.ui.Collapsible("Show more options",
                Ui.vbox(12, Ui.grid(2, 14, 10, Ui.row("Email", storeEmail), Ui.row("Tax registration no.", storeTaxId)),
                        Ui.grid(3, 14, 10, Ui.row("Currency symbol", currencySymbol, "Shown before amounts, e.g. Rs"),
                                Ui.row("Currency code", currencyCode, "e.g. PKR"),
                                Ui.row("Date format", dateFormat, "How dates appear on screen and receipts")),
                        Ui.grid(3, 14, 10, Ui.row("Warn when stock falls to", lowStock,
                                "Used for new products; change it per product too"))));
        JComponent c = Ui.vbox(12, Ui.heading("Shop details"), Ui.muted("Printed on every receipt and report."),
                Ui.grid(2, 14, 10, Ui.row("Shop name", storeName), Ui.row("Phone", storePhone)),
                Ui.row("Address", storeAddress), more);
        return form(c, () -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(Settings.STORE_NAME, storeName.getText());
            m.put(Settings.STORE_ADDRESS, storeAddress.getText());
            m.put(Settings.STORE_PHONE, storePhone.getText());
            m.put(Settings.STORE_EMAIL, storeEmail.getText());
            m.put(Settings.STORE_TAX_ID, storeTaxId.getText());
            m.put(Settings.CURRENCY_SYMBOL, currencySymbol.getText());
            m.put(Settings.CURRENCY_CODE, currencyCode.getText());
            m.put(Settings.DATE_FORMAT, (String) dateFormat.getSelectedItem());
            m.put(Settings.LOW_STOCK_DEFAULT, lowStock.getText());
            ctx.settings().save(m);
            frame.reloadAll(MainFrame.SETTINGS);
        });
    }

    // ------------------------------------------------------------ receipts

    private JComponent buildReceipts() {
        com.smartbillingpro.ui.Collapsible more = new com.smartbillingpro.ui.Collapsible("Show more receipt options",
                Ui.vbox(12, Ui.grid(3, 14, 10, Ui.row("Receipt number starts with", prefix, "e.g. INV \u2192 INV-000123"),
                                Ui.row("Usual way of paying", defaultMethod, "Chosen first on the Sales screen")),
                        Ui.row("Extra text at the top (optional)", area(header)),
                        Ui.heading("PDF copies"), autoPdf,
                        Ui.grid(2, 14, 10, Ui.row("PDF folder", pdfDir, "Leave empty for the default folder"),
                                Ui.row("PDF size", pdfSize))));
        JComponent c = Ui.vbox(12, Ui.heading("After each sale"),
                Ui.grid(2, 14, 10, Ui.row("Then", afterSale, "Print straight away, show a preview, or do nothing"),
                        Ui.row("Thank-you message at the bottom", area(footer))),
                Ui.heading("Tax"), taxEnabled,
                Ui.grid(3, 14, 10, Ui.row("Tax rate (%)", taxPercent), Ui.row("Name on the receipt", taxLabel, "e.g. GST")),
                more);
        return form(c, () -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(Settings.TAX_ENABLED, String.valueOf(taxEnabled.isSelected()));
            m.put(Settings.TAX_PERCENT, taxPercent.getText());
            m.put(Settings.TAX_LABEL, taxLabel.getText().isBlank() ? "Tax" : taxLabel.getText());
            m.put(Settings.RECEIPT_PREFIX, prefix.getText());
            m.put(Settings.RECEIPT_HEADER, header.getText());
            m.put(Settings.RECEIPT_FOOTER, footer.getText());
            PaymentMethod pm = (PaymentMethod) defaultMethod.getSelectedItem();
            m.put(Settings.POS_DEFAULT_METHOD, pm == null ? "" : String.valueOf(pm.id()));
            m.put(Settings.RECEIPT_AFTER_SALE, ((Settings.AfterSale) afterSale.getSelectedItem()).name());
            m.put(Settings.RECEIPT_AUTO_PDF, String.valueOf(autoPdf.isSelected()));
            m.put(Settings.RECEIPT_PDF_DIR, pdfDir.getText());
            m.put(Settings.RECEIPT_PDF_SIZE, pdfSize.getSelectedIndex() == 1 ? "THERMAL" : "A4");
            ctx.settings().save(m);
        });
    }

    // ------------------------------------------------------------ printer

    private JComponent buildPrinter() {
        paper.addActionListener(e -> {
            switch (paper.getSelectedIndex()) {
                case 0 -> {
                    paperMm.setText("80");
                    printableMm.setText("72");
                    chars.setText("48");
                }
                case 1 -> {
                    paperMm.setText("58");
                    printableMm.setText("48");
                    chars.setText("32");
                }
                default -> {
                }
            }
            paperMm.setEditable(paper.getSelectedIndex() == 2);
        });
        JButton test = Ui.secondary("Print test receipt", "print");
        test.addActionListener(e -> safely(() -> {
            savePrinter();
            Printers.printReceipt(sampleReceipt(), ctx.settings().get());
            Toast.ok(this, "Test receipt sent to the printer.");
        }));
        JButton refreshPrinters = Ui.ghost("Refresh list", "refresh");
        refreshPrinters.addActionListener(e -> loadPrinters());
        com.smartbillingpro.ui.Collapsible advanced = new com.smartbillingpro.ui.Collapsible("Show advanced printer settings",
                Ui.vbox(12, Ui.grid(2, 14, 10, Ui.row("How to print", mode, "Windows driver works for almost every printer"),
                                Ui.row("Paper width (mm)", paperMm)),
                        Ui.grid(3, 14, 10, Ui.row("Printable width (mm)", printableMm, "58 mm paper \u2192 48, 80 mm \u2192 72"),
                                Ui.row("Text size (pt)", fontPt, "Driver mode, default 8"),
                                Ui.row("Characters per line", chars, "ESC/POS: 32 for 58 mm, 48 for 80 mm")),
                        cut, drawer));
        JComponent c = Ui.vbox(12, Ui.heading("Receipt printer"),
                Ui.muted("<html>Install the printer's Windows driver first (USB receipt printers usually show up as e.g. "
                        + "\"POS-80\" or \"XP-58\"). Choose it here, then print a test receipt.</html>"),
                Ui.grid(2, 14, 10, Ui.row("Printer", printer), Ui.row("Paper roll", paper)),
                Ui.flowLeft(8, test, refreshPrinters), advanced);
        return form(c, this::savePrinter);
    }

    private void savePrinter() {
        Map<String, String> m = new LinkedHashMap<>();
        String p = (String) printer.getSelectedItem();
        m.put(Settings.PRINTER_NAME, p == null || p.startsWith("Windows default") ? "" : p);
        m.put(Settings.PRINTER_PAPER_MM, paperMm.getText());
        m.put(Settings.PRINTER_PRINTABLE_MM, printableMm.getText());
        m.put(Settings.PRINTER_MODE, ((Settings.PrinterMode) mode.getSelectedItem()).name());
        m.put(Settings.PRINTER_CHARS, chars.getText());
        m.put(Settings.PRINTER_FONT_PT, fontPt.getText());
        m.put(Settings.PRINTER_CUT, String.valueOf(cut.isSelected()));
        m.put(Settings.PRINTER_DRAWER, String.valueOf(drawer.isSelected()));
        ctx.settings().save(m);
    }

    private void loadPrinters() {
        String current = ctx.settings().get().printerName();
        printer.removeAllItems();
        printer.addItem("Windows default printer");
        for (String n : Printers.installedPrinters()) {
            printer.addItem(n);
        }
        if (!current.isBlank()) {
            boolean found = false;
            for (int i = 0; i < printer.getItemCount(); i++) {
                found |= printer.getItemAt(i).equals(current);
            }
            if (!found) {
                printer.addItem(current);
            }
            printer.setSelectedItem(current);
        }
    }

    private ReceiptData sampleReceipt() {
        Settings st = ctx.settings().get();
        List<String> lines = new java.util.ArrayList<>();
        if (!Text.blank(st.storeAddress())) {
            lines.add(st.storeAddress());
        }
        if (!Text.blank(st.storePhone())) {
            lines.add("Tel: " + st.storePhone());
        }
        return new ReceiptData(st.storeName(), lines, "PRINTER TEST", st.receiptFooter(), "TEST-000000",
                Dates.formatDateTime(Dates.now()), "Walk-in Customer", null, "Cash",
                List.of(new ReceiptData.Line("Sample product with a long name to test wrapping", "2 pcs", "150.00", "300.00", null),
                        new ReceiptData.Line("Loose sugar", "0.25 kg", "160.00", "40.00", "-5.00")),
                List.of(new ReceiptData.Total("Subtotal", "340.00", false), new ReceiptData.Total("Discount", "-5.00", false)),
                st.currencySymbol() + " 335.00", List.of(new ReceiptData.Total("Paid (Cash)", "500.00", false),
                        new ReceiptData.Total("Change", "165.00", false)), null, "Paper " + st.paperWidthMm() + " mm");
    }

    // ------------------------------------------------------------ payment methods & units

    private JComponent buildLists() {
        methods.text("Payment method", PaymentMethod::name, 200).text("Type", m -> m.cash() ? "Cash" : "Non-cash", 100)
                .status("Status", m -> m.active() ? new DataTable.Status("Active", Ui.Tone.GOOD)
                        : new DataTable.Status("Hidden", Ui.Tone.NEUTRAL));
        JButton addMethod = Ui.secondary("Add", "plus");
        addMethod.addActionListener(e -> {
            String n = Msg.ask(this, "New payment method", "Name (e.g. JazzCash, Easypaisa, Cheque):", "");
            if (n != null) {
                safely(() -> {
                    ctx.settings().addPaymentMethod(n, false);
                    refresh();
                });
            }
        });
        JButton renameMethod = Ui.secondary("Rename", "edit");
        renameMethod.addActionListener(e -> {
            PaymentMethod m = methods.selected();
            if (m == null) {
                return;
            }
            String n = Msg.ask(this, "Rename payment method", "New name:", m.name());
            if (n != null) {
                safely(() -> {
                    ctx.settings().updatePaymentMethod(m.id(), n, m.cash(), m.active());
                    refresh();
                });
            }
        });
        JButton toggleMethod = Ui.ghost("Hide / show", null);
        toggleMethod.addActionListener(e -> {
            PaymentMethod m = methods.selected();
            if (m != null) {
                safely(() -> {
                    ctx.settings().updatePaymentMethod(m.id(), m.name(), m.cash(), !m.active());
                    refresh();
                });
            }
        });

        units.text("Code", Unit::code, 70).text("Unit", Unit::name, 140).text("Measures", u -> u.dimension().label(), 90)
                .text("Equals", u -> u.dimension() == Dimension.COUNT ? u.factor().stripTrailingZeros().toPlainString() + " pcs"
                        : u.factor().stripTrailingZeros().toPlainString() + " " + base(u.dimension()), 110)
                .status("Status", u -> u.active() ? new DataTable.Status("Active", Ui.Tone.GOOD)
                        : new DataTable.Status("Hidden", Ui.Tone.NEUTRAL));
        JButton addUnit = Ui.secondary("Add unit", "plus");
        addUnit.addActionListener(e -> addUnit());
        JButton toggleUnit = Ui.ghost("Hide / show", null);
        toggleUnit.addActionListener(e -> {
            Unit u = units.selected();
            if (u != null) {
                safely(() -> {
                    ctx.inventory().setUnitActive(u.id(), !u.active());
                    refresh();
                });
            }
        });
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.add(Ui.vbox(4, Ui.heading("Payment methods"), Ui.muted("Shown at the till and on payments.")), BorderLayout.NORTH);
        left.add(Ui.card(methods, 4), BorderLayout.CENTER);
        left.add(Ui.flowLeft(6, addMethod, renameMethod, toggleMethod), BorderLayout.SOUTH);
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        right.add(Ui.vbox(4, Ui.heading("Units of measurement"),
                Ui.muted("Weight units convert automatically (e.g. 250 g of a per-kg product).")), BorderLayout.NORTH);
        right.add(Ui.card(units, 4), BorderLayout.CENTER);
        right.add(Ui.flowLeft(6, addUnit, toggleUnit), BorderLayout.SOUTH);
        JPanel p = new JPanel(new java.awt.GridLayout(1, 2, 16, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(left);
        p.add(right);
        return p;
    }

    private static String base(Dimension d) {
        return switch (d) {
            case WEIGHT -> "g";
            case VOLUME -> "ml";
            case LENGTH -> "cm";
            default -> "";
        };
    }

    private void addUnit() {
        if (new UnitDialog().open(460)) {
            Toast.ok(this, "Unit added.");
            refresh();
        }
    }

    /** Add a unit of measurement, e.g. a dozen = 12 pieces. */
    private final class UnitDialog extends com.smartbillingpro.ui.FormDialog {
        private final JTextField code = Ui.field("e.g. dz");
        private final JTextField name = Ui.field("e.g. Dozen");
        private final JComboBox<Dimension> dim = new JComboBox<>(Dimension.values());
        private final JTextField factor = Ui.field("e.g. 12");

        UnitDialog() {
            super(SettingsPage.this, "New unit", "Add unit");
            setSubtitle("For example a dozen is 12 pieces, a pao is 250 grams.");
            setBody(Ui.vbox(12, Ui.grid(2, 12, 0, Ui.row("Name", name), Ui.row("Short code", code)),
                    Ui.grid(2, 12, 0, Ui.row("It measures", dim), Ui.row("One of it equals", factor,
                            "pieces / grams / ml / cm"))));
        }

        @Override
        protected boolean onSave() {
            BigDecimal f;
            try {
                f = new BigDecimal(factor.getText().trim());
            } catch (NumberFormatException e) {
                factor.requestFocusInWindow();
                throw new com.smartbillingpro.db.AppException("Please type how much one of it equals, e.g. 12.");
            }
            ctx.inventory().addUnit(code.getText(), name.getText(), (Dimension) dim.getSelectedItem(), f);
            return true;
        }
    }

    // ------------------------------------------------------------ backup

    private JComponent buildBackup() {
        backupStatus.setFont(Theme.bold(15));
        JButton now = Ui.primary("Back up now", "backup");
        now.addActionListener(e -> runBackup(null));
        JButton toFolder = Ui.secondary("Back up to USB / other folder…", "upload");
        toFolder.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Choose where to save this backup");
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                runBackup(fc.getSelectedFile().toPath());
            }
        });
        JButton change = Ui.secondary("Change backup folder…", "folder");
        change.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(ctx.backups().backupDir().toFile());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                safely(() -> {
                    ctx.backups().setBackupDir(fc.getSelectedFile().toPath());
                    refresh();
                    Toast.ok(this, "Backups will be saved in " + fc.getSelectedFile());
                });
            }
        });
        JButton open = Ui.ghost("Open folder", "folder");
        open.addActionListener(e -> openFolder(ctx.backups().backupDir()));
        autoBackup.addActionListener(e -> ctx.backups().setAutoEnabled(autoBackup.isSelected()));
        keep.addChangeListener(e -> safely(() -> ctx.backups().setKeepCount((Integer) keep.getValue())));

        backups.col("Created", DataTable.Kind.DATETIME, b -> Dates.stored(b.created()), 160)
                .text("Type", b -> switch (b.kind()) {
                    case AUTO -> "Daily (automatic)";
                    case MANUAL -> "Manual";
                    case SAFETY -> "Safety copy";
                }, 130)
                .text("Size", b -> String.format("%,.1f MB", b.sizeBytes() / 1048576.0), 80)
                .text("File", b -> b.file().getFileName().toString(), 300);
        backups.setEmptyText("No backups yet. Click \"Back up now\".");
        JButton restoreSelected = Ui.danger("Restore selected…", "refresh");
        restoreSelected.addActionListener(e -> {
            BackupService.BackupInfo b = backups.selected();
            if (b != null) {
                restore(b.file());
            }
        });
        JButton restoreFile = Ui.danger("Restore from file…", "folder");
        restoreFile.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(ctx.backups().backupDir().toFile());
            fc.setFileFilter(new FileNameExtensionFilter("Smart Billing Pro backup (*.db)", "db"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                restore(fc.getSelectedFile().toPath());
            }
        });

        JComponent top = Ui.vbox(10, backupStatus, backupFolder, Ui.flowLeft(8, now, toFolder, change, open),
                Ui.flowLeft(10, autoBackup, Ui.muted("Keep the last"), keep, Ui.muted("daily backups")),
                Ui.muted("<html>Tip: choose a USB drive or another disk as the backup folder so a disk failure "
                        + "cannot take your backups with it. Backups never need the internet.</html>"));
        Card topCard = Ui.card(top, 18);
        JPanel listPanel = new JPanel(new BorderLayout(0, 8));
        listPanel.setOpaque(false);
        listPanel.add(Ui.heading("Available backups"), BorderLayout.NORTH);
        listPanel.add(Ui.card(backups, 4), BorderLayout.CENTER);
        listPanel.add(Ui.flowLeft(6, restoreSelected, restoreFile,
                Ui.muted("Restoring first saves a safety copy of your current data.")), BorderLayout.SOUTH);
        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        p.add(topCard, BorderLayout.NORTH);
        p.add(listPanel, BorderLayout.CENTER);
        return p;
    }

    private void runBackup(Path folder) {
        frame.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new SwingWorker<BackupService.BackupInfo, Void>() {
            @Override
            protected BackupService.BackupInfo doInBackground() {
                return folder == null ? ctx.backups().createBackup(BackupService.Kind.MANUAL)
                        : ctx.backups().createBackup(BackupService.Kind.MANUAL, folder);
            }

            @Override
            protected void done() {
                frame.setCursor(null);
                try {
                    BackupService.BackupInfo b = get();
                    Toast.ok(SettingsPage.this, "Backup saved and verified: " + b.file().getFileName());
                } catch (Exception e) {
                    Msg.error(SettingsPage.this, e.getCause() == null ? e : e.getCause());
                }
                frame.updateBackupStatus();
                refresh();
            }
        }.execute();
    }

    private void restore(Path file) {
        BackupService.Validation v = BackupService.validate(file);
        if (!v.ok()) {
            Msg.error(this, "This backup cannot be restored:\n" + v.message());
            return;
        }
        String info = "Backup: " + file.getFileName() + "\n\nIt contains " + v.products() + " products, " + v.sales()
                + " sales and " + v.customers() + " customers" + (v.lastSale() == null ? "" : ", last sale "
                + Dates.formatDateTime(v.lastSale())) + ".\n\nAll current data will be REPLACED by this backup. "
                + "A safety copy of the current data is saved first, so this can be undone.";
        if (!Msg.confirm(this, "Restore backup", info, "Restore")) {
            return;
        }
        try {
            ctx.restore(file);
            frame.reloadAll(MainFrame.SETTINGS);
            Msg.info(frame, "Restore complete", "Your data has been restored from " + file.getFileName() + ".");
        } catch (RuntimeException e) {
            Msg.error(this, e);
            frame.reloadAll(MainFrame.SETTINGS);
        }
    }

    private void openFolder(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception e) {
            Msg.info(this, "Folder", dir.toString());
        }
    }

    // ------------------------------------------------------------ security & data

    private JComponent buildSecurity() {
        JPasswordField pin1 = new JPasswordField(10);
        JPasswordField pin2 = new JPasswordField(10);
        JButton setPin = Ui.primary("Set PIN", "lock");
        setPin.addActionListener(e -> safely(() -> {
            String a = new String(pin1.getPassword());
            if (!a.equals(new String(pin2.getPassword()))) {
                throw new com.smartbillingpro.db.AppException("The two PINs do not match.");
            }
            ctx.settings().setPin(a);
            pin1.setText("");
            pin2.setText("");
            frame.pinChanged();
            refresh();
            Toast.ok(this, "PIN saved. It will be asked when the app starts.");
        }));
        JButton removePin = Ui.ghost("Remove PIN", "close");
        removePin.addActionListener(e -> {
            if (Msg.confirm(this, "Remove PIN", "Anyone at this computer will be able to open the app.", "Remove PIN")) {
                safely(() -> {
                    ctx.settings().setPin(null);
                    frame.pinChanged();
                    refresh();
                });
            }
        });
        JButton openData = Ui.secondary("Open data folder", "folder");
        openData.addActionListener(e -> openFolder(ctx.paths().dataDir()));
        JButton importOld = Ui.secondary("Import from previous version…", "download");
        importOld.addActionListener(e -> importLegacy());
        JComponent c = Ui.vbox(12, Ui.heading("Startup PIN (optional)"),
                Ui.muted("Protects the app with a 4–8 digit PIN when it opens and when you click the lock icon."),
                pinState, Ui.grid(3, 12, 0, Ui.row("New PIN", pin1), Ui.row("Repeat PIN", pin2)),
                Ui.flowLeft(6, setPin, removePin),
                Ui.heading("Your data"),
                Ui.muted("<html>All data is stored only on this computer:<br><b>" + ctx.paths().databaseFile()
                        + "</b><br>No internet connection or online account is used.</html>"),
                Ui.flowLeft(6, openData, importOld),
                Ui.heading("About"),
                Ui.muted(AppPaths.APP_NAME + " " + AppPaths.VERSION + " (offline edition) • Java "
                        + System.getProperty("java.version")));
        return form(c, null);
    }

    private void importLegacy() {
        File legacy = AppPaths.legacyDatabase();
        JFileChooser fc = new JFileChooser(legacy.getParentFile());
        fc.setSelectedFile(legacy);
        fc.setDialogTitle("Choose the previous version's database (shoppro.db)");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        safely(() -> {
            if (!ctx.legacyImport().currentDatabaseIsEmpty()) {
                throw new com.smartbillingpro.db.AppException("Import only works into an empty shop (before any products, "
                        + "customers or sales are added).");
            }
            List<LegacyImportService.LegacyStore> stores = ctx.legacyImport().scan(fc.getSelectedFile());
            if (stores.isEmpty()) {
                throw new com.smartbillingpro.db.AppException("No products or customers were found in that file.");
            }
            if (new LegacyImportDialog(this, ctx, fc.getSelectedFile(), stores).open(560)) {
                frame.reloadAll(MainFrame.INVENTORY);
            }
        });
    }

    // ------------------------------------------------------------ load

    @Override
    public void refresh() {
        Settings st = ctx.settings().get();
        storeName.setText(st.storeName());
        storeAddress.setText(st.storeAddress());
        storePhone.setText(st.storePhone());
        storeEmail.setText(st.storeEmail());
        storeTaxId.setText(st.storeTaxId());
        currencySymbol.setText(st.currencySymbol());
        currencyCode.setText(st.currencyCode());
        dateFormat.setSelectedItem(st.dateFormat());
        lowStock.setText(String.valueOf(st.lowStockDefault()));

        taxEnabled.setSelected(st.taxEnabled());
        taxPercent.setText(st.taxPercent().stripTrailingZeros().toPlainString());
        taxLabel.setText(st.taxLabel());
        prefix.setText(st.receiptPrefix());
        header.setText(st.receiptHeader());
        footer.setText(st.receiptFooter());
        defaultMethod.removeAllItems();
        for (PaymentMethod m : ctx.settings().paymentMethods(true)) {
            defaultMethod.addItem(m);
            if (st.defaultMethodId() != null && m.id() == st.defaultMethodId()) {
                defaultMethod.setSelectedItem(m);
            }
        }
        afterSale.setSelectedItem(st.afterSale());
        autoPdf.setSelected(st.autoPdf());
        pdfDir.setText(st.pdfDir());
        pdfSize.setSelectedIndex("THERMAL".equals(st.pdfSize()) ? 1 : 0);

        loadPrinters();
        paper.setSelectedIndex(st.paperWidthMm() == 80 ? 0 : st.paperWidthMm() == 58 ? 1 : 2);
        paperMm.setText(String.valueOf(st.paperWidthMm()));
        paperMm.setEditable(paper.getSelectedIndex() == 2);
        printableMm.setText(String.valueOf(st.printableWidthMm()));
        mode.setSelectedItem(st.printerMode());
        chars.setText(String.valueOf(st.charsPerLine()));
        fontPt.setText(String.valueOf(st.receiptFontPt()));
        cut.setSelected(st.cutPaper());
        drawer.setSelected(st.openDrawer());

        methods.setRows(ctx.settings().paymentMethods(false));
        units.setRows(ctx.inventory().units(false));

        String last = ctx.backups().lastBackupTime();
        boolean failed = "FAILED".equals(ctx.backups().lastStatus());
        backupStatus.setForeground(failed ? Theme.BAD : last.isBlank() ? Theme.WARN : Theme.GOOD);
        backupStatus.setText(failed ? "⚠ Last backup failed: " + ctx.backups().lastError()
                : last.isBlank() ? "No backup has been made yet" : "✓ Last backup: " + Dates.formatDateTime(last));
        backupFolder.setText("Backup folder: " + ctx.backups().backupDir());
        autoBackup.setSelected(ctx.backups().autoEnabled());
        keep.setValue(ctx.backups().keepCount());
        backups.setRows(ctx.backups().list());

        pinState.setText(st.pinEnabled() ? "✓ A PIN is set." : "No PIN is set.");
    }
}
