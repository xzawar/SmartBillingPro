package com.smartbillingpro.model;

/** A recorded action for the audit log. */
public class AuditEntry {
    private int id;
    private String timestamp = "";
    private String username = "";
    private String action = "";
    private String details = "";

    public AuditEntry() {
    }

    public AuditEntry(String timestamp, String username, String action, String details) {
        this.timestamp = timestamp;
        this.username = username;
        this.action = action;
        this.details = details;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }
}
