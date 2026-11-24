package com.smartbillingpro.print;

import java.awt.Color;

/** Measures without drawing (used to find a receipt's height before printing). */
public final class MeasureCanvas implements DocCanvas {

    private final DocCanvas metrics;

    public MeasureCanvas(DocCanvas metrics) {
        this.metrics = metrics;
    }

    @Override
    public float textWidth(String text, DocFont font) {
        return metrics.textWidth(text, font);
    }

    @Override
    public float ascent(DocFont font) {
        return metrics.ascent(font);
    }

    @Override
    public float lineHeight(DocFont font) {
        return metrics.lineHeight(font);
    }

    @Override
    public void text(String text, float x, float baselineY, DocFont font, Color color) {
        // measuring only
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float width, Color color) {
        // measuring only
    }

    @Override
    public void fillRect(float x, float y, float w, float h, Color color) {
        // measuring only
    }
}
