package com.smartbillingpro.app;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Database;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.repo.AuditRepo;
import com.smartbillingpro.repo.ExpenseRepo;
import com.smartbillingpro.repo.HeldSaleRepo;
import com.smartbillingpro.repo.LedgerRepo;
import com.smartbillingpro.repo.PartyRepo;
import com.smartbillingpro.repo.PaymentRepo;
import com.smartbillingpro.repo.ProductRepo;
import com.smartbillingpro.repo.PurchaseRepo;
import com.smartbillingpro.repo.ReferenceRepo;
import com.smartbillingpro.repo.SaleRepo;
import com.smartbillingpro.repo.SequenceRepo;
import com.smartbillingpro.repo.SettingsRepo;
import com.smartbillingpro.repo.StockRepo;
import com.smartbillingpro.service.BackupService;
import com.smartbillingpro.service.DashboardService;
import com.smartbillingpro.service.ExpenseService;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.service.LegacyImportService;
import com.smartbillingpro.service.PartyService;
import com.smartbillingpro.service.PurchaseService;
import com.smartbillingpro.service.ReportService;
import com.smartbillingpro.service.SaleService;
import com.smartbillingpro.service.SettingsService;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Wires the local database, repositories and services together. There is a
 * single store and a single operator, so there is no login, session or tenant.
 */
public final class AppContext implements AutoCloseable {

    private final AppPaths paths;
    private final AppConfig config;
    private final BackupService backups;
    private Database database;

    private SettingsService settings;
    private InventoryService inventory;
    private SaleService sales;
    private PartyService parties;
    private PurchaseService purchases;
    private ExpenseService expenses;
    private ReportService reports;
    private DashboardService dashboard;
    private LegacyImportService legacyImport;
    private AuditRepo audit;

    private ScheduledExecutorService scheduler;
    private volatile Consumer<String> backupListener = s -> { };

    public AppContext(AppPaths paths) {
        this.paths = paths;
        this.config = new AppConfig(paths.configFile());
        this.backups = new BackupService(paths.databaseFile(), paths, config);
        open();
    }

    private void open() {
        database = new Database(paths.databaseFile(), paths.safetyBackupDir());
        database.open();
        Db db = database.db();
        audit = new AuditRepo(db);
        SettingsRepo settingsRepo = new SettingsRepo(db);
        ReferenceRepo refs = new ReferenceRepo(db);
        PartyRepo partyRepo = new PartyRepo(db);
        ProductRepo productRepo = new ProductRepo(db);
        StockRepo stockRepo = new StockRepo(db);
        SaleRepo saleRepo = new SaleRepo(db);
        PurchaseRepo purchaseRepo = new PurchaseRepo(db);
        PaymentRepo paymentRepo = new PaymentRepo(db);
        LedgerRepo ledgerRepo = new LedgerRepo(db);
        ExpenseRepo expenseRepo = new ExpenseRepo(db);
        SequenceRepo sequences = new SequenceRepo(db);
        HeldSaleRepo held = new HeldSaleRepo(db);

        settings = new SettingsService(db, settingsRepo, refs, audit);
        inventory = new InventoryService(db, productRepo, stockRepo, refs, audit);
        sales = new SaleService(db, saleRepo, productRepo, stockRepo, paymentRepo, ledgerRepo, partyRepo, refs,
                sequences, held, audit, settings);
        parties = new PartyService(db, partyRepo, ledgerRepo, paymentRepo, refs, sequences, audit);
        purchases = new PurchaseService(db, purchaseRepo, productRepo, stockRepo, partyRepo, ledgerRepo, paymentRepo,
                refs, sequences, audit);
        expenses = new ExpenseService(db, expenseRepo, paymentRepo, refs, sequences, audit);
        reports = new ReportService(db, settings);
        dashboard = new DashboardService(db, reports, ledgerRepo);
        legacyImport = new LegacyImportService(db, productRepo, stockRepo, partyRepo, refs, settingsRepo, settings, audit);
    }

    // ------------------------------------------------------------ automatic backups

    /** Start the daily automatic backup check (runs in the background). */
    public void startBackupScheduler(Consumer<String> listener) {
        this.backupListener = listener == null ? s -> { } : listener;
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-backup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::autoBackupTick, 15, 30 * 60, TimeUnit.SECONDS);
    }

    private void autoBackupTick() {
        try {
            BackupService.BackupInfo info = backups.runAutoIfDue();
            if (info != null) {
                backupListener.accept("OK");
            }
        } catch (RuntimeException e) {
            backupListener.accept("FAILED: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ restore

    /**
     * Restore a backup: validate it, make a safety copy of the current data,
     * swap the database file and reopen. If the restored file cannot be opened
     * the previous database is put back automatically.
     */
    public synchronized BackupService.Validation restore(Path backupFile) {
        BackupService.Validation v = BackupService.validate(backupFile);
        if (!v.ok()) {
            throw new AppException(v.message());
        }
        synchronized (backups) {
            return restoreLocked(backupFile, v);
        }
    }

    private BackupService.Validation restoreLocked(Path backupFile, BackupService.Validation v) {
        backups.createBackup(BackupService.Kind.SAFETY);
        database.close();
        try {
            backups.replaceDatabaseFile(backupFile);
            try {
                open();
            } catch (RuntimeException e) {
                backups.rollbackRestore();
                open();
                throw new AppException("The backup could not be opened, your previous data was kept: " + e.getMessage(), e);
            }
            backups.finishRestore();
            audit.log("RESTORE", "Restored from " + backupFile.getFileName());
            return v;
        } catch (RuntimeException e) {
            if (database == null || isClosed()) {
                open();
            }
            throw e;
        }
    }

    private boolean isClosed() {
        try {
            return database.db() == null || database.db().connection().isClosed();
        } catch (java.sql.SQLException e) {
            return true;
        }
    }

    // ------------------------------------------------------------ accessors

    public AppPaths paths() {
        return paths;
    }

    public AppConfig config() {
        return config;
    }

    public Database database() {
        return database;
    }

    public SettingsService settings() {
        return settings;
    }

    public InventoryService inventory() {
        return inventory;
    }

    public SaleService sales() {
        return sales;
    }

    public PartyService parties() {
        return parties;
    }

    public PurchaseService purchases() {
        return purchases;
    }

    public ExpenseService expenses() {
        return expenses;
    }

    public ReportService reports() {
        return reports;
    }

    public DashboardService dashboard() {
        return dashboard;
    }

    public BackupService backups() {
        return backups;
    }

    public LegacyImportService legacyImport() {
        return legacyImport;
    }

    public AuditRepo audit() {
        return audit;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (database != null) {
            database.close();
        }
    }
}
