package com.smartbillingpro.model;

import java.math.BigDecimal;

/** A single line on a sale. */
public class InvoiceItem {
    private int productId;
    private String sku = "";
    private String name = "";
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal costPrice = BigDecimal.ZERO;
    private int quantity;

    public InvoiceItem() {
    }

    public InvoiceItem(int productId, String sku, String name, BigDecimal unitPrice, int quantity) {
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getLineProfit() {
        return unitPrice.subtract(costPrice).multiply(BigDecimal.valueOf(quantity));
    }
}
