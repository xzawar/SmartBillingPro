package com.smartbillingpro.repository;

/**
 * Holds the id of the store whose data the app is currently working with.
 * Every store-scoped DAO reads this value, so changing it (at login) instantly
 * switches every query to that store's own products, customers, sales and
 * settings. Store id 0 means "no store" (used by the administrator).
 */
public class StoreSession {

    private int storeId;

    public StoreSession(int storeId) {
        this.storeId = storeId;
    }

    public int getStoreId() {
        return storeId;
    }

    public void setStoreId(int storeId) {
        this.storeId = storeId;
    }
}
