package com.smartbillingpro;

import com.smartbillingpro.app.AppConfig;
import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.service.BackupService;
import com.smartbillingpro.service.Cart;
import com.smartbillingpro.service.SaleService;
import com.smartbillingpro.util.Qty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static com.smartbillingpro.TestSupport.check;
import static com.smartbillingpro.TestSupport.eq;
import static com.smartbillingpro.TestSupport.fails;
import static com.smartbillingpro.TestSupport.money;

final class BackupTests {

    private BackupTests() {
    }

    private static void sell(AppContext ctx, long variant) {
        Cart c = new Cart();
        c.add(ctx.inventory().sellable(variant), Qty.of(1));
        ctx.sales().complete(c, new SaleService.PaymentInput(TestSupport.cashMethod(ctx), money("100"), null));
    }

    static void testBackupCreateValidateRestore() throws Exception {
        Path dir = TestSupport.tempDir();
        try (AppContext ctx = TestSupport.newContext(dir)) {
            long v = TestSupport.simpleProduct(ctx, "Biscuit", "BC", "50", "100", "10", "pcs");
            sell(ctx, v);
            BackupService.BackupInfo b = ctx.backups().createBackup(BackupService.Kind.MANUAL);
            check(Files.isRegularFile(b.file()), "backup file written");
            check(b.file().getFileName().toString().matches("SmartBillingPro_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_manual\\.db"),
                    "dated file name: " + b.file().getFileName());
            check(Files.isRegularFile(b.file().resolveSibling(b.file().getFileName() + ".sha256")), "checksum written");
            BackupService.Validation val = BackupService.validate(b.file());
            check(val.ok(), "backup validates: " + val.message());
            eq(1L, val.sales(), "backup contains the sale");
            eq("OK", ctx.backups().lastStatus(), "status recorded");

            // more activity after the backup
            sell(ctx, v);
            sell(ctx, v);
            eq(3, ctx.sales().list(null, null, null, null, null, null, 99).size(), "3 sales now");
            ctx.restore(b.file());
            eq(1, ctx.sales().list(null, null, null, null, null, null, 99).size(), "restored to 1 sale");
            eq(Qty.of(9), ctx.inventory().sellable(v).stockQty(), "stock restored");
            check(ctx.backups().list().stream().anyMatch(x -> x.kind() == BackupService.Kind.SAFETY),
                    "safety copy made before restore");
            // the app keeps working after a restore
            sell(ctx, v);
            eq("INV-000002", ctx.sales().list(null, null, null, null, null, null, 1).get(0).receiptNo(), "numbering after restore");
        }
    }

    static void testCorruptBackupIsRejected() throws Exception {
        Path dir = TestSupport.tempDir();
        try (AppContext ctx = TestSupport.newContext(dir)) {
            long v = TestSupport.simpleProduct(ctx, "Juice", "JU", "50", "100", "10", "pcs");
            sell(ctx, v);
            BackupService.BackupInfo b = ctx.backups().createBackup(BackupService.Kind.MANUAL);
            // tamper with the backup
            byte[] bytes = Files.readAllBytes(b.file());
            bytes[bytes.length / 2] ^= 0x5A;
            Path damaged = dir.resolve("damaged.db");
            Files.write(damaged, bytes);
            Files.copy(b.file().resolveSibling(b.file().getFileName() + ".sha256"), dir.resolve("damaged.db.sha256"));
            BackupService.Validation val = BackupService.validate(damaged);
            check(!val.ok(), "tampered backup rejected");
            fails(() -> ctx.restore(damaged), "damaged");
            Path text = dir.resolve("notes.db");
            Files.writeString(text, "hello");
            check(!BackupService.validate(text).ok(), "random file rejected");
            Path foreign = dir.resolve("foreign.db");
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + foreign); Statement st = c.createStatement()) {
                st.execute("CREATE TABLE x (a)");
            }
            check(!BackupService.validate(foreign).ok(), "other sqlite db rejected");
            eq(1, ctx.sales().list(null, null, null, null, null, null, 9).size(), "live data untouched");
        }
    }

    static void testAutoBackupOncePerDayAndRetention() throws Exception {
        Path dir = TestSupport.tempDir();
        try (AppContext ctx = TestSupport.newContext(dir)) {
            TestSupport.simpleProduct(ctx, "Salt", "SL", "10", "20", "5", "pcs");
            check(ctx.backups().autoBackupDue(), "due on first run");
            check(ctx.backups().runAutoIfDue() != null, "auto backup made");
            check(!ctx.backups().autoBackupDue(), "not due again today");
            eq(null, ctx.backups().runAutoIfDue(), "second call does nothing");
            ctx.backups().setKeepCount(3);
            for (int i = 0; i < 5; i++) {
                ctx.config().set(AppConfig.BACKUP_LAST_AUTO_DATE, "2000-01-0" + (i + 1));
                Thread.sleep(1100); // distinct timestamps in file names
                ctx.backups().runAutoIfDue();
            }
            long autos = ctx.backups().list().stream().filter(b -> b.kind() == BackupService.Kind.AUTO).count();
            eq(3L, autos, "only the newest 3 automatic backups are kept");
            ctx.backups().setBackupDir(dir.resolve("usb-drive"));
            BackupService.BackupInfo b = ctx.backups().createBackup(BackupService.Kind.MANUAL);
            check(b.file().startsWith(dir.resolve("usb-drive")), "custom destination used");
        }
    }

    static void testBackupWhileWriting() throws Exception {
        Path dir = TestSupport.tempDir();
        try (AppContext ctx = TestSupport.newContext(dir)) {
            long v = TestSupport.simpleProduct(ctx, "Chips", "CH", "10", "20", "500", "pcs");
            Thread t = new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    ctx.backups().createBackup(BackupService.Kind.MANUAL);
                }
            });
            t.start();
            for (int i = 0; i < 40; i++) {
                sell(ctx, v);
            }
            t.join();
            for (BackupService.BackupInfo b : ctx.backups().list()) {
                check(BackupService.validate(b.file()).ok(), "every backup taken during sales is valid");
            }
            eq(40, ctx.sales().list(null, null, null, null, null, null, 99).size(), "all sales saved");
        }
    }
}
