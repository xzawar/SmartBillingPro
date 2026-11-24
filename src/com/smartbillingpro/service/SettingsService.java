package com.smartbillingpro.service;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.repo.AuditRepo;
import com.smartbillingpro.repo.SettingsRepo;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Passwords;

import java.math.BigDecimal;
import java.util.Map;

public final class SettingsService {

    private final Db db;
    private final SettingsRepo repo;
    private final AuditRepo audit;
    private Settings current;

    public SettingsService(Db db, SettingsRepo repo, AuditRepo audit) {
        this.db = db;
        this.repo = repo;
        this.audit = audit;
        reload();
    }

    public Settings get() {
        return current;
    }

    public void reload() {
        current = new Settings(repo.loadAll());
        Money.setSymbol(current.currencySymbol());
        Dates.setDisplayPattern(current.dateFormat());
    }

    /** Validate and save a group of settings atomically. */
    public void save(Map<String, String> input) {
        Map<String, String> changes = new java.util.LinkedHashMap<>(input);
        validate(changes);
        db.txRun(() -> {
            for (Map.Entry<String, String> e : changes.entrySet()) {
                if (Settings.PIN_HASH.equals(e.getKey())) {
                    continue; // PIN only via setPin()
                }
                repo.put(e.getKey(), e.getValue() == null ? "" : e.getValue().trim());
            }
            audit.log("SETTINGS", "Updated " + String.join(", ", changes.keySet()));
        });
        reload();
    }

    private void validate(Map<String, String> c) {
        if (c.containsKey(Settings.STORE_NAME) && c.get(Settings.STORE_NAME).isBlank()) {
            throw new AppException("Store name is required.");
        }
        if (c.containsKey(Settings.TAX_PERCENT)) {
            try {
                BigDecimal pct = new BigDecimal(c.get(Settings.TAX_PERCENT).trim().isEmpty() ? "0" : c.get(Settings.TAX_PERCENT).trim());
                if (pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new AppException("Tax must be between 0 and 100 percent.");
                }
                c.put(Settings.TAX_PERCENT, pct.stripTrailingZeros().toPlainString());
            } catch (NumberFormatException e) {
                throw new AppException("Tax percent must be a number, e.g. 17 or 17.5.");
            }
        }
        if (c.containsKey(Settings.RECEIPT_PREFIX) && !c.get(Settings.RECEIPT_PREFIX).trim().matches("[A-Za-z0-9]{1,8}")) {
            throw new AppException("Receipt prefix must be 1-8 letters or digits, e.g. INV.");
        }
        for (String key : new String[] {Settings.PRINTER_PAPER_MM, Settings.PRINTER_PRINTABLE_MM, Settings.PRINTER_CHARS,
            Settings.LOW_STOCK_DEFAULT, Settings.PRINTER_FONT_PT}) {
            if (c.containsKey(key) && !c.get(key).trim().matches("\\d{1,4}")) {
                throw new AppException("Please enter a whole number for " + key.substring(key.indexOf('.') + 1).replace('_', ' ') + ".");
            }
        }
        if (c.containsKey(Settings.PRINTER_PAPER_MM)) {
            int paper = Integer.parseInt(c.get(Settings.PRINTER_PAPER_MM).trim());
            if (paper < 30 || paper > 120) {
                throw new AppException("Paper width must be between 30 and 120 mm.");
            }
            if (c.containsKey(Settings.PRINTER_PRINTABLE_MM)
                    && Integer.parseInt(c.get(Settings.PRINTER_PRINTABLE_MM).trim()) > paper) {
                throw new AppException("The printable width cannot be wider than the paper.");
            }
        }
        if (c.containsKey(Settings.CURRENCY_SYMBOL) && c.get(Settings.CURRENCY_SYMBOL).trim().length() > 6) {
            throw new AppException("Currency symbol is too long (max 6 characters).");
        }
    }

    public void setPin(String pin) {
        if (pin != null && !pin.isBlank() && !pin.matches("\\d{4,8}")) {
            throw new AppException("The PIN must be 4 to 8 digits.");
        }
        db.txRun(() -> {
            repo.put(Settings.PIN_HASH, pin == null || pin.isBlank() ? "" : Passwords.hash(pin));
            audit.log("SECURITY", pin == null || pin.isBlank() ? "Startup PIN removed" : "Startup PIN set");
        });
        reload();
    }

    public boolean checkPin(String pin) {
        return !current.pinEnabled() || Passwords.verify(pin, current.pinHash());
    }
}
