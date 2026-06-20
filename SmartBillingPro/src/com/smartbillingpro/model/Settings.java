package com.smartbillingpro.model;

import java.math.BigDecimal;

/** Shop-wide configuration shown on receipts and used for tax/loyalty. */
public class Settings {
    private String businessName = "My Shop";
    private String address = "";
    private String phone = "";
    private String invoicePrefix = "INV";
    private BigDecimal defaultTaxPercent = BigDecimal.ZERO;
    private String currencySymbol = "Rs ";
    private String footerMessage = "Thank you for shopping with us!";
    private int lowStockThreshold = 5;
    private int returnPolicyDays = 7;
    private int loyaltyPointsPer100 = 1;

    public static Settings defaults() {
        return new Settings();
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getInvoicePrefix() {
        return invoicePrefix;
    }

    public void setInvoicePrefix(String invoicePrefix) {
        this.invoicePrefix = invoicePrefix;
    }

    public BigDecimal getDefaultTaxPercent() {
        return defaultTaxPercent;
    }

    public void setDefaultTaxPercent(BigDecimal defaultTaxPercent) {
        this.defaultTaxPercent = defaultTaxPercent;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public String getFooterMessage() {
        return footerMessage;
    }

    public void setFooterMessage(String footerMessage) {
        this.footerMessage = footerMessage;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public int getReturnPolicyDays() {
        return returnPolicyDays;
    }

    public void setReturnPolicyDays(int returnPolicyDays) {
        this.returnPolicyDays = returnPolicyDays;
    }

    public int getLoyaltyPointsPer100() {
        return loyaltyPointsPer100;
    }

    public void setLoyaltyPointsPer100(int loyaltyPointsPer100) {
        this.loyaltyPointsPer100 = loyaltyPointsPer100;
    }
}
