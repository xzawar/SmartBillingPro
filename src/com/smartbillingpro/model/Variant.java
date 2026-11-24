package com.smartbillingpro.model;

/**
 * One sellable version of a product (a size, colour, pack...). A simple product
 * has exactly one variant with an empty name. Prices are minor units, stock and
 * minimum stock are thousandths of the product unit.
 */
public record Variant(long id, long productId, String name, String barcode, String sku,
                      long costPrice, long salePrice, long stockQty, long minStock, boolean active) {

    public boolean isDefault() {
        return name == null || name.isBlank();
    }
}
