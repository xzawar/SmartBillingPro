package com.smartbillingpro.service;

import com.smartbillingpro.model.Settings;
import com.smartbillingpro.model.Store;
import com.smartbillingpro.repository.SettingsDao;
import com.smartbillingpro.repository.StoreDao;
import com.smartbillingpro.util.Dates;

import java.util.List;

/**
 * Manages stores (the tenants this software is sold to). Creating a store sets
 * up a brand-new, empty workspace: it gets its own settings row seeded from the
 * store's name, and starts with no products, customers or sales.
 */
public class StoreService {

    private final StoreDao storeDao;
    private final SettingsDao settingsDao;
    private final AuditService auditService;

    public StoreService(StoreDao storeDao, SettingsDao settingsDao, AuditService auditService) {
        this.storeDao = storeDao;
        this.settingsDao = settingsDao;
        this.auditService = auditService;
    }

    public List<Store> all() {
        return storeDao.findAll();
    }

    public Store findById(int id) {
        return storeDao.findById(id);
    }

    public int count() {
        return storeDao.count();
    }

    public Store create(Store store) {
        if (store.getCreatedAt() == null || store.getCreatedAt().isBlank()) {
            store.setCreatedAt(Dates.now());
        }
        if (store.getInvoiceCounter() <= 0) {
            store.setInvoiceCounter(1000);
        }
        store.setActive(true);
        storeDao.insert(store);

        // Seed this store's own settings so its receipts already show its name.
        Settings defaults = Settings.defaults();
        defaults.setBusinessName(store.getName());
        if (store.getPhone() != null && !store.getPhone().isBlank()) {
            defaults.setPhone(store.getPhone());
        }
        if (store.getAddress() != null && !store.getAddress().isBlank()) {
            defaults.setAddress(store.getAddress());
        }
        settingsDao.saveFor(store.getId(), defaults);

        auditService.log("STORE_CREATE", store.getName());
        return store;
    }

    public void update(Store store) {
        storeDao.update(store);
        auditService.log("STORE_UPDATE", store.getName());
    }
}
