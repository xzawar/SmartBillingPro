package com.smartbillingpro;

import com.smartbillingpro.app.AppConfig;
import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.app.AppPaths;
import com.smartbillingpro.app.InstanceLock;
import com.smartbillingpro.service.LegacyImportService;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.PinDialog;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.dialogs.LegacyImportDialog;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

/**
 * Smart Billing Pro - offline point of sale for a single shop.
 *
 * <p>Starts without any network access: the data lives in a local SQLite file
 * (see {@link AppPaths}) and a daily backup runs in the background.</p>
 */
public final class SmartBillingApp {

    private SmartBillingApp() {
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        AppPaths paths = AppPaths.detect();
        redirectLogs(paths);
        SwingUtilities.invokeLater(() -> start(paths));
    }

    private static void redirectLogs(AppPaths paths) {
        try {
            Files.createDirectories(paths.logDir());
            File log = paths.logDir().resolve("app-" + LocalDate.now() + ".log").toFile();
            PrintStream ps = new PrintStream(new java.io.FileOutputStream(log, true), true, "UTF-8");
            System.setErr(ps);
        } catch (Exception ignored) {
            // logging is optional
        }
    }

    private static void start(AppPaths paths) {
        Theme.install();
        InstanceLock lock = new InstanceLock();
        if (!lock.acquire(paths.lockFile())) {
            JOptionPane.showMessageDialog(null, "Smart Billing Pro is already open on this computer.",
                    AppPaths.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
            return;
        }
        AppContext ctx;
        try {
            ctx = new AppContext(paths);
        } catch (RuntimeException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Smart Billing Pro could not open its data:\n\n" + e.getMessage()
                    + "\n\nData folder: " + paths.dataDir()
                    + "\n\nYou can restore a backup by copying it over the database file, or contact support.",
                    AppPaths.APP_NAME, JOptionPane.ERROR_MESSAGE);
            lock.release();
            System.exit(1);
            return;
        }
        if (!PinDialog.unlock(null, ctx, false)) {
            ctx.close();
            lock.release();
            System.exit(0);
            return;
        }
        Runnable exit = () -> {
            try {
                ctx.backups().runAutoIfDue();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            ctx.close();
            lock.release();
            System.exit(0);
        };
        MainFrame frame = new MainFrame(ctx, exit);
        frame.setVisible(true);
        ctx.startBackupScheduler(status -> frame.updateBackupStatus());
        offerLegacyImport(frame, ctx);
    }

    /** First run after upgrading from the online version: offer to bring the old products across. */
    private static void offerLegacyImport(MainFrame frame, AppContext ctx) {
        try {
            if (ctx.config().getBool(AppConfig.LEGACY_IMPORT_ASKED, false) || !ctx.legacyImport().currentDatabaseIsEmpty()) {
                return;
            }
            File legacy = AppPaths.legacyDatabase();
            List<LegacyImportService.LegacyStore> stores = ctx.legacyImport().scan(legacy);
            if (stores.isEmpty()) {
                return;
            }
            ctx.config().set(AppConfig.LEGACY_IMPORT_ASKED, "true");
            if (new LegacyImportDialog(frame, ctx, legacy, stores).open(560)) {
                frame.reloadAll(MainFrame.INVENTORY);
            }
        } catch (RuntimeException e) {
            Msg.error(frame, e);
        }
    }
}
