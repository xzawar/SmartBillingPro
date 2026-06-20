package com.smartbillingpro.service;

import com.smartbillingpro.model.Settings;
import com.smartbillingpro.model.User;
import com.smartbillingpro.repository.AuditDao;
import com.smartbillingpro.repository.CustomerDao;
import com.smartbillingpro.repository.Database;
import com.smartbillingpro.repository.InvoiceDao;
import com.smartbillingpro.repository.ProductDao;
import com.smartbillingpro.repository.PurchaseDao;
import com.smartbillingpro.repository.SettingsDao;
import com.smartbillingpro.repository.StoreDao;
import com.smartbillingpro.repository.StoreSession;
import com.smartbillingpro.repository.SupplierDao;
import com.smartbillingpro.repository.UserDao;

/**
 * Wires the database, DAOs and services together and holds session state
 * (the logged-in user, the active store and the loaded settings).
 *
 * <p>The {@link StoreSession} is the heart of the multi-tenant design: store
 * data is kept separate per store, and every store-scoped DAO reads the active
 * store id from this one shared session. When a user logs in we point the
 * session at that user's store, so each shop only ever sees its own products,
 * customers, sales and settings. The administrator (store id 0) only manages
 * stores and user accounts.</p>
 */
public class AppContext {

    private final Database database;
    private final StoreSession storeSession;

    private final AuditService auditService;
    private final SettingsService settingsService;
    private final AuthService authService;
    private final StoreService storeService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final SupplierService supplierService;
    private final BillingService billingService;
    private final InvoiceService invoiceService;
    private final PurchaseService purchaseService;
    private final ReportService reportService;

    private User currentUser;
    private Settings settings;

    public AppContext() {
        database = new Database(Database.defaultDbFile());
        database.init();

        // Start with no active store; login will set it.
        storeSession = new StoreSession(0);

        // Global DAOs (not tied to a single store).
        UserDao userDao = new UserDao(database);
        StoreDao storeDao = new StoreDao(database);

        // Store-scoped DAOs all read the active store from the shared session.
        AuditDao auditDao = new AuditDao(database, storeSession);
        SupplierDao supplierDao = new SupplierDao(database, storeSession);
        ProductDao productDao = new ProductDao(database, storeSession);
        CustomerDao customerDao = new CustomerDao(database, storeSession);
        SettingsDao settingsDao = new SettingsDao(database, storeSession);
        InvoiceDao invoiceDao = new InvoiceDao(database, storeSession);
        PurchaseDao purchaseDao = new PurchaseDao(database, storeSession);

        this.auditService = new AuditService(auditDao, this);
        this.settingsService = new SettingsService(settingsDao, auditService);
        this.authService = new AuthService(userDao, auditService);
        this.storeService = new StoreService(storeDao, settingsDao, auditService);
        this.inventoryService = new InventoryService(productDao);
        this.customerService = new CustomerService(customerDao);
        this.supplierService = new SupplierService(supplierDao);
        this.billingService = new BillingService();
        this.invoiceService = new InvoiceService(database, invoiceDao, productDao, customerDao, auditService, this);
        this.purchaseService = new PurchaseService(database, purchaseDao, productDao, auditService);
        this.reportService = new ReportService(invoiceService, inventoryService);

        this.settings = settingsService.load();
    }

    public AuditService getAuditService() {
        return auditService;
    }

    public SettingsService getSettingsService() {
        return settingsService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public StoreService getStoreService() {
        return storeService;
    }

    public InventoryService getInventoryService() {
        return inventoryService;
    }

    public CustomerService getCustomerService() {
        return customerService;
    }

    public SupplierService getSupplierService() {
        return supplierService;
    }

    public BillingService getBillingService() {
        return billingService;
    }

    public InvoiceService getInvoiceService() {
        return invoiceService;
    }

    public PurchaseService getPurchaseService() {
        return purchaseService;
    }

    public ReportService getReportService() {
        return reportService;
    }

    public StoreSession getStoreSession() {
        return storeSession;
    }

    public int getCurrentStoreId() {
        return storeSession.getStoreId();
    }

    public User getCurrentUser() {
        return currentUser;
    }

    /** Sets the logged-in user and points the session at that user's store. */
    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
        storeSession.setStoreId(currentUser == null ? 0 : currentUser.getStoreId());
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public void reloadSettings() {
        this.settings = settingsService.load();
    }

    public void shutdown() {
        database.close();
    }
}
