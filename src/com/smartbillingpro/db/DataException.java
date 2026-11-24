package com.smartbillingpro.db;

/** An unexpected database failure (disk problem, corrupted file, bug). */
public class DataException extends RuntimeException {

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}
