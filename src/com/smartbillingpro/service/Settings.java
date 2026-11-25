package com.smartbillingpro.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed, read-only view of the store settings (stored as key/value rows). */
public final class Settings {

    public static final String STORE_NAME = "store.name";
    public static final String STORE_ADDRESS = "store.address";
    public static final String STORE_PHONE = "store.phone";
    public static final String STORE_EMAIL = "store.email";
    public static final String STORE_TAX_ID = "store.tax_id";
    public static final String CURRENCY_SYMBOL = "currency.symbol";
    public static final String CURRENCY_CODE = "currency.code";
    public static final String DATE_FORMAT = "format.date";
    public static final String TAX_ENABLED = "tax.enabled";
    public static final String TAX_PERCENT = "tax.percent";
    public static final String TAX_LABEL = "tax.label";
    public static final String RECEIPT_PREFIX = "receipt.prefix";
    public static final String RECEIPT_HEADER = "receipt.header";
    public static final String RECEIPT_FOOTER = "receipt.footer";
    public static final String RECEIPT_AFTER_SALE = "receipt.after_sale";
    public static final String RECEIPT_AUTO_PDF = "receipt.auto_pdf";
    public static final String RECEIPT_PDF_DIR = "receipt.pdf_dir";
    public static final String RECEIPT_PDF_SIZE = "receipt.pdf_size";
    public static final String PRINTER_NAME = "printer.name";
    public static final String PRINTER_PAPER_MM = "printer.paper_mm";
    public static final String PRINTER_PRINTABLE_MM = "printer.printable_mm";
    public static final String PRINTER_MODE = "printer.mode";
    public static final String PRINTER_CHARS = "printer.chars";
    public static final String PRINTER_CUT = "printer.cut";
    public static final String PRINTER_DRAWER = "printer.drawer";
    public static final String PRINTER_FONT_PT = "printer.font_pt";
    public static final String POS_DEFAULT_METHOD = "pos.default_method";
    public static final String LOW_STOCK_DEFAULT = "inventory.low_stock_default";
    public static final String PIN_HASH = "security.pin_hash";

    /** What happens after a sale is completed. */
    public enum AfterSale {
        PRINT("Print receipt automatically"), PREVIEW("Show receipt (print or save PDF)"), NONE("Do nothing");

        private final String label;

        AfterSale(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** How receipts reach the thermal printer. */
    public enum PrinterMode {
        DRIVER("Windows printer driver (recommended)"), ESCPOS("Raw ESC/POS text");

        private final String label;

        PrinterMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static Map<String, String> defaults() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put(STORE_NAME, "My Store");
        d.put(STORE_ADDRESS, "");
        d.put(STORE_PHONE, "");
        d.put(STORE_EMAIL, "");
        d.put(STORE_TAX_ID, "");
        d.put(CURRENCY_SYMBOL, "Rs");
        d.put(CURRENCY_CODE, "PKR");
        d.put(DATE_FORMAT, "dd/MM/yyyy");
        d.put(TAX_ENABLED, "false");
        d.put(TAX_PERCENT, "0");
        d.put(TAX_LABEL, "Tax");
        d.put(RECEIPT_PREFIX, "INV");
        d.put(RECEIPT_HEADER, "");
        d.put(RECEIPT_FOOTER, "Thank you for shopping with us!");
        d.put(RECEIPT_AFTER_SALE, AfterSale.PREVIEW.name());
        d.put(RECEIPT_AUTO_PDF, "false");
        d.put(RECEIPT_PDF_DIR, "");
        d.put(RECEIPT_PDF_SIZE, "A4");
        d.put(PRINTER_NAME, "");
        d.put(PRINTER_PAPER_MM, "80");
        d.put(PRINTER_PRINTABLE_MM, "72");
        d.put(PRINTER_MODE, PrinterMode.DRIVER.name());
        d.put(PRINTER_CHARS, "48");
        d.put(PRINTER_CUT, "true");
        d.put(PRINTER_DRAWER, "false");
        d.put(PRINTER_FONT_PT, "8");
        d.put(POS_DEFAULT_METHOD, "");
        d.put(LOW_STOCK_DEFAULT, "5");
        d.put(PIN_HASH, "");
        return d;
    }

    private final Map<String, String> values;

    Settings(Map<String, String> stored) {
        Map<String, String> merged = defaults();
        merged.putAll(stored);
        this.values = Map.copyOf(merged);
    }

    public String get(String key) {
        return values.getOrDefault(key, "");
    }

    public Map<String, String> asMap() {
        return values;
    }

    private boolean bool(String key) {
        return Boolean.parseBoolean(get(key));
    }

    private int integer(String key, int fallback) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String storeName() {
        return get(STORE_NAME);
    }

    public String storeAddress() {
        return get(STORE_ADDRESS);
    }

    public String storePhone() {
        return get(STORE_PHONE);
    }

    public String storeEmail() {
        return get(STORE_EMAIL);
    }

    public String storeTaxId() {
        return get(STORE_TAX_ID);
    }

    public String currencySymbol() {
        return get(CURRENCY_SYMBOL);
    }

    public String currencyCode() {
        return get(CURRENCY_CODE);
    }

    public String dateFormat() {
        return get(DATE_FORMAT);
    }

    public boolean taxEnabled() {
        return bool(TAX_ENABLED);
    }

    public BigDecimal taxPercent() {
        try {
            return new BigDecimal(get(TAX_PERCENT).trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** The tax percentage to apply to new sales (zero when tax is disabled). */
    public BigDecimal effectiveTaxPercent() {
        return taxEnabled() ? taxPercent() : BigDecimal.ZERO;
    }

    public String taxLabel() {
        return get(TAX_LABEL);
    }

    public String receiptPrefix() {
        String p = get(RECEIPT_PREFIX).trim();
        return p.isEmpty() ? "INV" : p;
    }

    public String receiptHeader() {
        return get(RECEIPT_HEADER);
    }

    public String receiptFooter() {
        return get(RECEIPT_FOOTER);
    }

    public AfterSale afterSale() {
        try {
            return AfterSale.valueOf(get(RECEIPT_AFTER_SALE));
        } catch (IllegalArgumentException e) {
            return AfterSale.PREVIEW;
        }
    }

    public boolean autoPdf() {
        return bool(RECEIPT_AUTO_PDF);
    }

    public String pdfDir() {
        return get(RECEIPT_PDF_DIR);
    }

    /** "A4" or "THERMAL". */
    public String pdfSize() {
        return "THERMAL".equals(get(RECEIPT_PDF_SIZE)) ? "THERMAL" : "A4";
    }

    public String printerName() {
        return get(PRINTER_NAME);
    }

    public int paperWidthMm() {
        return Math.max(30, integer(PRINTER_PAPER_MM, 80));
    }

    public int printableWidthMm() {
        int printable = integer(PRINTER_PRINTABLE_MM, paperWidthMm() - 8);
        return Math.max(25, Math.min(printable, paperWidthMm()));
    }

    public PrinterMode printerMode() {
        try {
            return PrinterMode.valueOf(get(PRINTER_MODE));
        } catch (IllegalArgumentException e) {
            return PrinterMode.DRIVER;
        }
    }

    public int charsPerLine() {
        return Math.max(20, Math.min(80, integer(PRINTER_CHARS, paperWidthMm() <= 58 ? 32 : 48)));
    }

    public boolean cutPaper() {
        return bool(PRINTER_CUT);
    }

    public boolean openDrawer() {
        return bool(PRINTER_DRAWER);
    }

    public int receiptFontPt() {
        return Math.max(6, Math.min(14, integer(PRINTER_FONT_PT, 8)));
    }

    public Long defaultMethodId() {
        try {
            return Long.parseLong(get(POS_DEFAULT_METHOD).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int lowStockDefault() {
        return Math.max(0, integer(LOW_STOCK_DEFAULT, 5));
    }

    public String pinHash() {
        return get(PIN_HASH);
    }

    public boolean pinEnabled() {
        return !pinHash().isBlank();
    }
}
