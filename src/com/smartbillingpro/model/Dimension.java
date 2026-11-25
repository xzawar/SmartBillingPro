package com.smartbillingpro.model;

/** What a unit measures. Units of the same dimension can be converted. */
public enum Dimension {
    COUNT("Count"), WEIGHT("Weight"), VOLUME("Volume"), LENGTH("Length");

    private final String label;

    Dimension(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
