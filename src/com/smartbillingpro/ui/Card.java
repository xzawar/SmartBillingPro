package com.smartbillingpro.ui;

import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** White rounded surface with a hairline border. */
public class Card extends JPanel {

    private boolean outlined = true;

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBackground(Theme.SURFACE);
    }

    /** Tinted cards separate by colour alone (no outline). */
    public void setOutlined(boolean outlined) {
        this.outlined = outlined;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS * 2, Theme.RADIUS * 2);
        if (outlined) {
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS * 2, Theme.RADIUS * 2);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
