package com.smartbillingpro.ui.components;

import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A flat, rounded button with hover and pressed shading. */
public class RoundedButton extends JButton {

    private Color base;
    private int arc = 14;

    public RoundedButton(String text, Color base, Color foreground) {
        super(text);
        this.base = base;
        setForeground(foreground);
        setFont(Theme.bold(13));
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    }

    public void setBaseColor(Color base) {
        this.base = base;
        repaint();
    }

    public void setArc(int arc) {
        this.arc = arc;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill = base;
        boolean translucent = base.getAlpha() < 255;
        if (!isEnabled()) {
            fill = Theme.withAlpha(base, 120);
        } else if (getModel().isPressed()) {
            // Translucent (ghost) buttons get more solid on press; solid buttons darken.
            fill = translucent ? Theme.withAlpha(base, Math.min(255, base.getAlpha() + 90))
                    : Theme.darken(base, 0.82);
        } else if (getModel().isRollover()) {
            // Hover: lift ghost buttons by raising opacity, deepen solid buttons gently.
            fill = translucent ? Theme.withAlpha(base, Math.min(255, base.getAlpha() + 55))
                    : Theme.darken(base, 0.90);
        }
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }
}
