package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;

/**
 * A friendly "nothing here yet" screen: a soft illustration, one short title,
 * one helpful line and (usually) the one action that fixes it.
 */
public final class EmptyState extends JPanel {

    private final JLabel title = new JLabel();
    private final JLabel text = new JLabel();
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final boolean compact;
    private String iconName;

    public EmptyState(String icon, String titleText, String body) {
        this(icon, titleText, body, false);
    }

    public EmptyState(String icon, String titleText, String body, boolean compact) {
        super(new GridBagLayout());
        this.compact = compact;
        this.iconName = icon;
        setOpaque(false);
        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        Illustration art = new Illustration();
        art.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(Theme.bold(compact ? 15 : 19));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        text.setFont(Theme.font(compact ? 13 : 14));
        text.setForeground(Theme.MUTED);
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        text.setHorizontalAlignment(SwingConstants.CENTER);
        actions.setOpaque(false);
        actions.setAlignmentX(Component.CENTER_ALIGNMENT);
        inner.add(art);
        inner.add(Box.createVerticalStrut(compact ? 10 : 16));
        inner.add(title);
        inner.add(Box.createVerticalStrut(6));
        inner.add(text);
        inner.add(Box.createVerticalStrut(compact ? 12 : 18));
        inner.add(actions);
        inner.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(inner);
        setTitle(titleText);
        setText(body);
    }

    public EmptyState setTitle(String t) {
        title.setText(t);
        return this;
    }

    public EmptyState setText(String t) {
        int width = compact ? 260 : 380;
        String safe = t == null ? "" : t.replace("&", "&amp;").replace("<", "&lt;");
        text.setText("<html><div style='text-align:center;width:" + width + "px'>" + safe + "</div></html>");
        return this;
    }

    public EmptyState setIcon(String icon) {
        this.iconName = icon;
        repaint();
        return this;
    }

    /** The main way out of this empty state (lime pill). */
    public EmptyState action(String label, String icon, Runnable r) {
        JButton b = Ui.primary(label, icon);
        b.addActionListener(e -> r.run());
        actions.add(b);
        return this;
    }

    /** A quieter alternative. */
    public EmptyState secondary(String label, Runnable r) {
        JButton b = Ui.secondary(label, null);
        b.addActionListener(e -> r.run());
        actions.add(b);
        return this;
    }

    public void clearActions() {
        actions.removeAll();
        actions.revalidate();
    }

    /** First action button, if any (for keyboard focus). */
    public JButton firstAction() {
        return actions.getComponentCount() > 0 && actions.getComponent(0) instanceof JButton b ? b : null;
    }

    /** Soft circle with the icon: calm, never alarming. */
    private final class Illustration extends JComponent {
        Illustration() {
            int s = compact ? 64 : 96;
            setPreferredSize(new Dimension(s, s));
            setMaximumSize(new Dimension(s, s));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = Math.min(getWidth(), getHeight());
            int x = (getWidth() - s) / 2;
            g2.setColor(Theme.ACCENT_SOFT);
            g2.fillOval(x, 0, s, s);
            int inner = (int) (s * 0.62);
            g2.setColor(Theme.alpha(Theme.ACCENT, 140));
            g2.fillOval(x + (s - inner) / 2, (s - inner) / 2, inner, inner);
            int icon = (int) (s * 0.36);
            Icons.get(iconName, icon, Theme.ACCENT_INK).paintIcon(this, g2, x + (s - icon) / 2, (s - icon) / 2);
            g2.dispose();
        }
    }
}
