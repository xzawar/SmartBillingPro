package com.smartbillingpro.service;

import com.smartbillingpro.model.Supplier;
import com.smartbillingpro.repository.SupplierDao;

import java.util.List;

/** Supplier directory operations. */
public class SupplierService {

    private final SupplierDao supplierDao;

    public SupplierService(SupplierDao supplierDao) {
        this.supplierDao = supplierDao;
    }

    public List<Supplier> all() {
        return supplierDao.findAll();
    }

    public Supplier findById(int id) {
        return supplierDao.findById(id);
    }

    public Supplier create(Supplier supplier) {
        return supplierDao.insert(supplier);
    }

    public void update(Supplier supplier) {
        supplierDao.update(supplier);
    }

    public void delete(Supplier supplier) {
        supplierDao.delete(supplier);
    }

    public int count() {
        return supplierDao.count();
    }
}
