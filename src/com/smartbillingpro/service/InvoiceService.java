package com.smartbillingpro.service;

import com.smartbillingpro.model.Invoice;
import com.smartbillingpro.model.InvoiceItem;
import com.smartbillingpro.model.InvoiceStatus;
import com.smartbillingpro.model.Settings;
import com.smartbillingpro.repository.CustomerDao;
import com.smartbillingpro.repository.Database;
import com.smartbillingpro.repository.DataAccessException;
import com.smartbillingpro.repository.InvoiceDao;
import com.smartbillingpro.repository.ProductDao;
import com.smartbillingpro.util.Dates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Saves sales and processes voids/refunds, keeping stock in sync. */
public class InvoiceService {

    private final Database database;
    private final InvoiceDao invoiceDao;
    private final ProductDao productDao;
    private final CustomerDao customerDao;
    private final AuditService auditService;
    private final AppContext context;

    public InvoiceService(Database database, InvoiceDao invoiceDao, ProductDao productDao,
                          CustomerDao customerDao, AuditService auditService, AppContext context) {
        this.database = database;
        this.invoiceDao = invoiceDao;
        this.productDao = productDao;
        this.customerDao = customerDao;
        this.auditService = auditService;
        this.context = context;
    }

    /** Persist a sale: assign a number, store it, draw down stock and award loyalty. */
    public Invoice completeSale(Invoice inv) {
        return database.tx(conn -> {
            Settings settings = context.getSettings();
            inv.setInvoiceNumber(invoiceDao.nextInvoiceNumber(settings.getInvoicePrefix()));
            inv.setCreatedAt(Dates.now());
            int loyalty = 0;
            boolean hasCustomer = inv.getCustomerId() != null && inv.getCustomerId() > 0;
            if (hasCustomer && settings.getLoyaltyPointsPer100() > 0) {
                loyalty = inv.getGrandTotal().divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).intValue()
                        * settings.getLoyaltyPointsPer100();
            }
            inv.setLoyaltyEarned(loyalty);
            invoiceDao.insert(inv);
            for (InvoiceItem item : inv.getItems()) {
                productDao.decreaseStock(item.getProductId(), item.getQuantity());
            }
            if (loyalty > 0) {
                customerDao.addLoyalty(inv.getCustomerId(), loyalty);
            }
            auditService.log("SALE", inv.getInvoiceNumber());
            return inv;
        });
    }

    public void voidInvoice(int id, String reason) {
        reverse(id, reason, InvoiceStatus.VOIDED, "VOID");
    }

    public void refundInvoice(int id, String reason) {
        reverse(id, reason, InvoiceStatus.REFUNDED, "REFUND");
    }

    private void reverse(int id, String reason, InvoiceStatus newStatus, String action) {
        database.tx(conn -> {
            Invoice inv = invoiceDao.findById(id);
            if (inv == null) {
                throw new DataAccessException("Invoice not found.");
            }
            if (inv.getStatus() != InvoiceStatus.COMPLETED) {
                throw new DataAccessException("Only completed sales can be "
                        + (newStatus == InvoiceStatus.VOIDED ? "voided" : "refunded") + ".");
            }
            for (InvoiceItem item : inv.getItems()) {
                productDao.increaseStock(item.getProductId(), item.getQuantity());
            }
            invoiceDao.updateStatus(id, newStatus, reason);
            if (inv.getLoyaltyEarned() > 0 && inv.getCustomerId() != null && inv.getCustomerId() > 0) {
                customerDao.addLoyalty(inv.getCustomerId(), -inv.getLoyaltyEarned());
            }
            auditService.log(action, inv.getInvoiceNumber() + ": " + (reason == null ? "" : reason));
            return null;
        });
    }

    public List<Invoice> all() {
        return invoiceDao.findAll();
    }

    public Invoice load(int id) {
        return invoiceDao.findById(id);
    }
}
