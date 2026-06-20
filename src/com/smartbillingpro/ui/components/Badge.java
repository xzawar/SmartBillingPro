package com.smartbillingpro.ui.components;

import com.smartbillingpro.ui.theme.Theme;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/** A small rounded status pill, e.g. "Low Stock" or "Completed". */
public class Badge extends JLabel {

    private Color fill = Theme.withAlpha(Theme.INFO, 40);

    public Badge(String text) {
        super(text);
        setFont(Theme.bold(11));
        setForeground(Theme.TEXT);
        setHorizontalAlignment(CENTER);
        setOpaque(false);
    }

    public void setColors(Color textColor, Color background) {
        setForeground(textColor);
        this.fill = background;
        repaint();
    }

    @Override
    public Insets getInsets() {
        return new Insets(4, 12, 4, 12);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width + 8, size.height + 4);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
