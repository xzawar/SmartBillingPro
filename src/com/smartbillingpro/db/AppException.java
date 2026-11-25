package com.smartbillingpro.db;

/**
 * An error whose message is safe and meaningful to show to the shop owner,
 * e.g. "This barcode is already used by Coca Cola 1.5L".
 */
public class AppException extends RuntimeException {

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
