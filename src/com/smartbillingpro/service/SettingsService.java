package com.smartbillingpro.service;

import com.smartbillingpro.model.Settings;
import com.smartbillingpro.repository.SettingsDao;

/** Loads and saves shop settings. */
public class SettingsService {

    private final SettingsDao settingsDao;
    private final AuditService auditService;

    public SettingsService(SettingsDao settingsDao, AuditService auditService) {
        this.settingsDao = settingsDao;
        this.auditService = auditService;
    }

    public Settings load() {
        return settingsDao.load();
    }

    public boolean isEmpty() {
        return settingsDao.isEmpty();
    }

    public void save(Settings settings) {
        settingsDao.save(settings);
        auditService.log("SETTINGS", "Updated shop settings");
    }
}
