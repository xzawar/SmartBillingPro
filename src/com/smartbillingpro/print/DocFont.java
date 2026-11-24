package com.smartbillingpro.print;

/** Font request for document rendering (size in points). */
public record DocFont(float size, boolean bold) {

    public static DocFont regular(float size) {
        return new DocFont(size, false);
    }

    public static DocFont bold(float size) {
        return new DocFont(size, true);
    }
}
