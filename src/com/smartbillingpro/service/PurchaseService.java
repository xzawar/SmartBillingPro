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
