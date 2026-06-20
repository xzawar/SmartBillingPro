package com.smartbillingpro.repository;

/** Wraps low-level SQL failures so the UI can show a friendly message. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
