package com.smartbillingpro.model;

import java.math.BigDecimal;

/** A line on a stock-receiving purchase order. */
public class PurchaseOrderItem {
    private int id;
    private int productId;
    private String productName = "";
    private int quantity;
    private BigDecimal costPrice = BigDecimal.ZERO;

    public PurchaseOrderItem() {
    }

    public PurchaseOrderItem(int productId, String productName, int quantity, BigDecimal costPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.costPrice = costPrice;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getLineTotal() {
        return costPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
