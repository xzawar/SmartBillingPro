package com.smartbillingpro.service;

import com.smartbillingpro.model.Product;
import com.smartbillingpro.repository.ProductDao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Inventory operations and stock summaries. */
public class InventoryService {

    private final ProductDao productDao;

    public InventoryService(ProductDao productDao) {
        this.productDao = productDao;
    }

    public List<Product> all() {
        return productDao.findAll();
    }

    public List<Product> search(String term) {
        return productDao.search(term);
    }

    public Product findById(int id) {
        return productDao.findById(id);
    }

    public Product findBySku(String sku) {
        return productDao.findBySku(sku);
    }

    public Product create(Product product) {
        return productDao.insert(product);
    }

    public void update(Product product) {
        productDao.update(product);
    }

    public void delete(Product product) {
        productDao.delete(product);
    }

    public List<Product> lowStock(int threshold) {
        List<Product> list = new ArrayList<>();
        for (Product p : all()) {
            if (p.getQuantity() > 0 && p.getQuantity() <= threshold) {
                list.add(p);
            }
        }
        return list;
    }

    public List<Product> outOfStock() {
        List<Product> list = new ArrayList<>();
        for (Product p : all()) {
            if (p.getQuantity() <= 0) {
                list.add(p);
            }
        }
        return list;
    }

    public int productCount() {
        return productDao.count();
    }

    public int totalUnits() {
        int units = 0;
        for (Product p : all()) {
            units += p.getQuantity();
        }
        return units;
    }

    public BigDecimal retailValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (Product p : all()) {
            total = total.add(p.getSalePrice().multiply(BigDecimal.valueOf(p.getQuantity())));
        }
        return total;
    }

    public BigDecimal costValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (Product p : all()) {
            total = total.add(p.getCostPrice().multiply(BigDecimal.valueOf(p.getQuantity())));
        }
        return total;
    }

    /** Human-readable stock status for a product. */
    public static String stockStatus(Product product, int threshold) {
        if (product.getQuantity() <= 0) {
            return "Out of Stock";
        }
        if (product.getQuantity() <= threshold) {
            return "Low Stock";
        }
        return "In Stock";
    }
}
