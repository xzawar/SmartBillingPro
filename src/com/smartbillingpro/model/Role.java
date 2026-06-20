package com.smartbillingpro.model;

/**
 * Who a user is and what they are allowed to do.
 *
 * <ul>
 *   <li><b>ADMIN</b> - the software owner. Manages stores and staff accounts
 *       only; never sells or touches shop data.</li>
 *   <li><b>OWNER</b> - runs one store. Full access inside that store including
 *       its settings.</li>
 *   <li><b>MANAGER</b> - runs the floor: inventory, suppliers, purchases,
 *       reports, voids/refunds (no store settings).</li>
 *   <li><b>CASHIER</b> - sells and manages customers only.</li>
 * </ul>
 */
public enum Role {
    ADMIN("Administrator"),
    OWNER("Store Owner"),
    MANAGER("Manager"),
    CASHIER("Cashier");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** True only for the single software administrator. */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /** Only the administrator manages staff accounts. */
    public boolean canManageUsers() {
        return this == ADMIN;
    }

    /** Only the administrator creates and manages stores. */
    public boolean canManageStores() {
        return this == ADMIN;
    }

    public boolean canResetData() {
        return this == ADMIN;
    }

    /** The store owner configures their own shop settings and receipts. */
    public boolean canManageSettings() {
        return this == OWNER;
    }

    public boolean canManageInventory() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canManageSuppliers() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canViewReports() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canVoidInvoice() {
        return this == OWNER || this == MANAGER;
    }

    public boolean canProcessReturns() {
        return this == OWNER || this == MANAGER;
    }

    /**
     * Who is allowed to operate the till and sell items. Owners run the back
     * office and monitor data, but only managers and cashiers make sales.
     */
    public boolean canSell() {
        return this == MANAGER || this == CASHIER;
    }

    /** The store owner creates and manages their own managers and cashiers. */
    public boolean canManageStaff() {
        return this == OWNER;
    }

    public static Role fromName(String name) {
        if (name != null) {
            for (Role role : values()) {
                if (role.name().equalsIgnoreCase(name.trim())) {
                    return role;
                }
            }
        }
        return CASHIER;
    }
}
