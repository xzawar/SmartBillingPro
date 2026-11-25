package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A summary figure: a coloured icon tile, a label, the number as the hero, and a
 * one-line hint with an optional status pill. Tinted cards sit on a pastel
 * background and need no outline.
 */
public class StatCard extends Card {

    private final JLabel value = new JLabel("—");
    private final JLabel hint = new JLabel(" ");
    private final JLabel status = Ui.pill("", Ui.Tone.NEUTRAL);
    private final Theme.Tint tint;

    public StatCard(String title, String icon) {
        this(title, icon, null);
    }

    public StatCard(String title, String icon, Theme.Tint tint) {
        super(new BorderLayout(0, 8));
        this.tint = tint;
        if (tint != null) {
            setBackground(tint.bg);
            setOutlined(false);
        }
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        setPreferredSize(new Dimension(220, 138));
        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(14));
        t.setForeground(tint == null ? Theme.TEXT_2 : tint.icon.darker());
        t.setIcon(tile(icon, tint == null ? Theme.TEXT_2 : tint.icon, tint != null));
        t.setIconTextGap(10);
        value.setFont(Theme.bold(28));
        value.setForeground(Theme.TEXT);
        hint.setFont(Theme.font(12));
        hint.setForeground(Theme.MUTED);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(hint, BorderLayout.CENTER);
        status.setVisible(false);
        bottom.add(status, BorderLayout.EAST);
        add(t, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /** Rounded square with the glyph: solid colour + white glyph on tinted cards, soft tile otherwise. */
    private static Icon tile(String icon, Color color, boolean solid) {
        Icon glyph = Icons.get(icon, 18, solid ? Color.WHITE : color);
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(solid ? color : Theme.NEUTRAL_SOFT);
                g2.fillRoundRect(x, y, 32, 32, 12, 12);
                glyph.paintIcon(c, g2, x + 7, y + 7);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 32;
            }

            @Override
            public int getIconHeight() {
                return 32;
            }
        };
    }

    public void set(String valueText, String hintText) {
        value.setText(valueText);
        hint.setText(hintText == null || hintText.isEmpty() ? " " : hintText);
        value.setToolTipText(valueText);
    }

    /** Show a status pill (text + colour), or hide it with {@code null}. */
    public void status(String text, Ui.Tone tone) {
        if (text == null) {
            status.setVisible(false);
            value.setForeground(Theme.TEXT);
            return;
        }
        status.setText(text);
        Ui.setTone(status, tone);
        status.setVisible(true);
        value.setForeground(tone == Ui.Tone.BAD ? Theme.BAD : Theme.TEXT);
    }

    public Theme.Tint tint() {
        return tint;
    }
}
