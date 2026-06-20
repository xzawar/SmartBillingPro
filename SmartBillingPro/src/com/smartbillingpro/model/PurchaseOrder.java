package com.smartbillingpro.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** A stock-receiving order placed with a supplier. */
public class PurchaseOrder {
    private int id;
    private String reference = "";
    private int supplierId;
    private String supplierName = "";
    private String date = "";
    private String status = "Received";
    private List<PurchaseOrderItem> items = new ArrayList<>();
    private BigDecimal total = BigDecimal.ZERO;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<PurchaseOrderItem> getItems() {
        return items;
    }

    public void setItems(List<PurchaseOrderItem> items) {
        this.items = items;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public int getItemCount() {
        return items == null ? 0 : items.size();
    }
}
