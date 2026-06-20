package com.smartbillingpro.ui.components;

import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A left-aligned navigation item for the sidebar with an active state. */
public class SidebarButton extends JButton {

    private boolean active = false;

    public SidebarButton(String text) {
        super(text);
        setFont(Theme.bold(13));
        setForeground(Theme.SIDEBAR_TEXT);
        setHorizontalAlignment(LEFT);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 14));
    }

    public void setActive(boolean active) {
        this.active = active;
        setForeground(active ? Color.WHITE : Theme.SIDEBAR_TEXT);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (active) {
            g2.setColor(Theme.SIDEBAR_HOVER);
            g2.fillRoundRect(8, 2, getWidth() - 12, getHeight() - 4, 12, 12);
            g2.setColor(Theme.GOLD);
            g2.fillRoundRect(8, 8, 4, getHeight() - 16, 4, 4);
        } else if (getModel().isRollover()) {
            g2.setColor(Theme.withAlpha(Theme.SIDEBAR_HOVER, 120));
            g2.fillRoundRect(8, 2, getWidth() - 12, getHeight() - 4, 12, 12);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
