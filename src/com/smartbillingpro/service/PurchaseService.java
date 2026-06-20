package com.smartbillingpro.service;

import com.smartbillingpro.model.PurchaseOrder;
import com.smartbillingpro.model.PurchaseOrderItem;
import com.smartbillingpro.repository.Database;
import com.smartbillingpro.repository.ProductDao;
import com.smartbillingpro.repository.PurchaseDao;
import com.smartbillingpro.util.Dates;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Receives stock from suppliers and records purchase orders. */
public class PurchaseService {

    private static final DateTimeFormatter REFERENCE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Database database;
    private final PurchaseDao purchaseDao;
    private final ProductDao productDao;
    private final AuditService auditService;

    public PurchaseService(Database database, PurchaseDao purchaseDao, ProductDao productDao,
                           AuditService auditService) {
        this.database = database;
        this.purchaseDao = purchaseDao;
        this.productDao = productDao;
        this.auditService = auditService;
    }

    /** Save a purchase order and raise stock + refresh cost prices. */
    public PurchaseOrder receive(PurchaseOrder po) {
        return database.tx(conn -> {
            po.setReference("PO-" + LocalDateTime.now().format(REFERENCE));
            po.setDate(Dates.now());
            po.setStatus("Received");
            BigDecimal total = BigDecimal.ZERO;
            for (PurchaseOrderItem item : po.getItems()) {
                total = total.add(item.getLineTotal());
            }
            po.setTotal(total);
            purchaseDao.insert(po);
            for (PurchaseOrderItem item : po.getItems()) {
                productDao.increaseStock(item.getProductId(), item.getQuantity());
                productDao.updateCostPrice(item.getProductId(), Database.toText(item.getCostPrice()));
            }
            auditService.log("PURCHASE", po.getReference());
            return po;
        });
    }

    /** Delete a purchase order and reverse the stock it had added. */
    public void delete(int id) {
        database.tx(conn -> {
            PurchaseOrder po = purchaseDao.findById(id);
            if (po == null) {
                return null;
            }
            for (PurchaseOrderItem item : po.getItems()) {
                productDao.decreaseStock(item.getProductId(), item.getQuantity());
            }
            purchaseDao.delete(id);
            auditService.log("PURCHASE_DELETE", po.getReference());
            return null;
        });
    }

    /**
     * Update an existing purchase order. The stock added by the old lines is
     * reversed first, then the new lines are applied so inventory stays correct.
     */
    public PurchaseOrder update(PurchaseOrder po) {
        return database.tx(conn -> {
            PurchaseOrder existing = purchaseDao.findById(po.getId());
            if (existing == null) {
                throw new com.smartbillingpro.repository.DataAccessException("Purchase order not found.");
            }
            for (PurchaseOrderItem item : existing.getItems()) {
                productDao.decreaseStock(item.getProductId(), item.getQuantity());
            }
            BigDecimal total = BigDecimal.ZERO;
            for (PurchaseOrderItem item : po.getItems()) {
                total = total.add(item.getLineTotal());
            }
            po.setReference(existing.getReference());
            po.setDate(existing.getDate());
            po.setStatus("Received");
            po.setTotal(total);
            purchaseDao.update(po);
            purchaseDao.replaceItems(po);
            for (PurchaseOrderItem item : po.getItems()) {
                productDao.increaseStock(item.getProductId(), item.getQuantity());
                productDao.updateCostPrice(item.getProductId(), Database.toText(item.getCostPrice()));
            }
            auditService.log("PURCHASE_UPDATE", po.getReference());
            return po;
        });
    }

    public List<PurchaseOrder> all() {
        return purchaseDao.findAll();
    }

    public PurchaseOrder load(int id) {
        return purchaseDao.findById(id);
    }

    public int count() {
        return purchaseDao.count();
    }
}
