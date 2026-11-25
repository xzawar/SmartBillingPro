package com.smartbillingpro.print;

import java.awt.Color;

/**
 * A drawing surface measured in points (1/72 inch) with the origin at the top
 * left. Receipts and reports are laid out once against this interface and then
 * drawn either on screen / to a printer ({@link Graphics2DCanvas}) or into a PDF
 * ({@link PdfCanvas}), so every output looks the same.
 */
public interface DocCanvas {

    float textWidth(String text, DocFont font);

    /** Distance from the top of a line to its baseline. */
    float ascent(DocFont font);

    /** Recommended distance between consecutive baselines. */
    float lineHeight(DocFont font);

    void text(String text, float x, float baselineY, DocFont font, Color color);

    void line(float x1, float y1, float x2, float y2, float width, Color color);

    void fillRect(float x, float y, float w, float h, Color color);

    /** Dashed separator (thermal receipt style). */
    default void dashed(float x1, float y, float x2, Color color) {
        float dash = 3f;
        for (float x = x1; x < x2; x += dash * 2) {
            line(x, y, Math.min(x + dash, x2), y, 0.6f, color);
        }
    }
}
