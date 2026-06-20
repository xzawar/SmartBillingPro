package com.smartbillingpro.ui.components;

import com.smartbillingpro.ui.theme.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** A panel filled with a diagonal gradient between two colours. */
public class GradientPanel extends JPanel {

    private Color start = Theme.PRIMARY;
    private Color end = Theme.ACCENT;

    public GradientPanel() {
        setOpaque(false);
    }

    public GradientPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    public void setColors(Color start, Color end) {
        this.start = start;
        this.end = end;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
