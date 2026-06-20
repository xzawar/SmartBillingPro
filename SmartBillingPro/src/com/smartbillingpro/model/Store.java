package com.smartbillingpro.model;

/**
 * A single shop (tenant). Every store has its own products, customers,
 * suppliers, sales and settings - all kept separate by the store id.
 *
 * <p>The Administrator creates stores and then creates the staff accounts that
 * belong to each store. A brand-new store starts completely empty.
 */
public class Store {

    private int id;
    private String name = "";
    private String phone = "";
    private String address = "";
    private boolean active = true;
    private String createdAt = "";
    private int invoiceCounter = 1000;

    public Store() {
    }

    public Store(String name, String phone, String address) {
        this.name = name;
        this.phone = phone;
        this.address = address;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getInvoiceCounter() {
        return invoiceCounter;
    }

    public void setInvoiceCounter(int invoiceCounter) {
        this.invoiceCounter = invoiceCounter;
    }

    @Override
    public String toString() {
        return name;
    }
}
