package com.smartbillingpro.model;

import java.util.List;

/** A catalogue item with one or more {@link Variant}s. */
public record Product(long id, String name, Long categoryId, String categoryName, String description,
                      long unitId, String unitCode, boolean fractional, String weightValue, String weightUnit,
                      Long supplierId, String supplierName, boolean hasVariants, boolean active,
                      boolean hasImage, List<Variant> variants) {

    public long totalStock() {
        long total = 0;
        for (Variant v : variants) {
            if (v.active()) {
                total += v.stockQty();
            }
        }
        return total;
    }

    public Variant firstVariant() {
        return variants.isEmpty() ? null : variants.get(0);
    }

    public List<Variant> activeVariants() {
        return variants.stream().filter(Variant::active).toList();
    }

    @Override
    public String toString() {
        return name;
    }
}
