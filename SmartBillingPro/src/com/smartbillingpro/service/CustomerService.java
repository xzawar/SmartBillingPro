package com.smartbillingpro.service;

import com.smartbillingpro.model.Customer;
import com.smartbillingpro.repository.CustomerDao;

import java.util.List;

/** Customer directory operations. */
public class CustomerService {

    private final CustomerDao customerDao;

    public CustomerService(CustomerDao customerDao) {
        this.customerDao = customerDao;
    }

    public List<Customer> all() {
        return customerDao.findAll();
    }

    public List<Customer> search(String term) {
        return customerDao.search(term);
    }

    public Customer findById(int id) {
        return customerDao.findById(id);
    }

    public Customer findByPhone(String phone) {
        return customerDao.findByPhone(phone);
    }

    public Customer create(Customer customer) {
        return customerDao.insert(customer);
    }

    public void update(Customer customer) {
        customerDao.update(customer);
    }

    public void delete(Customer customer) {
        customerDao.delete(customer);
    }

    public int count() {
        return customerDao.count();
    }
}
