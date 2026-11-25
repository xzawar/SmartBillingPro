package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;

/**
 * The right-hand "details" panel used on list screens: a scrolling card that
 * shows one item, or a friendly hint when nothing is chosen. Also holds the
 * small building blocks (tiles, section titles) those panels share.
 */
public class Detail extends JPanel {

    private final CardLayout cards = new CardLayout();
    private final ScrollablePanel body = new ScrollablePanel(null);
    private final JScrollPane scroll;

    public Detail(EmptyState nothingChosen) {
        setLayout(cards);
        setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Card card = new Card(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        card.add(scroll, BorderLayout.CENTER);
        add(card, "item");
        Card empty = new Card(new BorderLayout());
        empty.add(nothingChosen, BorderLayout.CENTER);
        add(empty, "none");
        cards.show(this, "none");
    }

    /** Start filling the panel for a new item. */
    public void begin() {
        body.removeAll();
    }

    public void add(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(c);
    }

    /** Show what was added since {@link #begin()}, scrolled to the top. */
    public void done() {
        body.add(javax.swing.Box.createVerticalGlue());
        body.revalidate();
        body.repaint();
        cards.show(this, "item");
        javax.swing.SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    public void showNothing() {
        cards.show(this, "none");
    }

    // ------------------------------------------------------------ building blocks

    /** A soft tile with a label, a big value and a short coloured hint. */
    public static JComponent tile(String label, String value, String hint, Ui.Tone tone) {
        Card c = new Card(new BorderLayout(0, 4));
        c.setOutlined(false);
        c.setBackground(Theme.SURFACE_ALT);
        c.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JLabel l = Ui.muted(label);
        l.setFont(Theme.font(12));
        JLabel v = new JLabel(value);
        v.setFont(Theme.bold(18));
        v.setForeground(Theme.TEXT);
        JLabel h = new JLabel(hint == null || hint.isBlank() ? " " : hint);
        h.setFont(Theme.font(12));
        h.setForeground(tone == null || tone == Ui.Tone.NEUTRAL ? Theme.MUTED : tone.fg);
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        c.add(h, BorderLayout.SOUTH);
        return c;
    }

    /** A row of equal tiles. */
    public static JComponent tiles(JComponent... tiles) {
        JPanel grid = new JPanel(new java.awt.GridLayout(1, tiles.length, 10, 0));
        grid.setOpaque(false);
        for (JComponent t : tiles) {
            grid.add(t);
        }
        grid.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        return capped(grid);
    }

    /** Section title with an optional link-style button on the right. */
    public static JComponent section(String text, JComponent right) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(15));
        l.setForeground(Theme.TEXT);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(l, BorderLayout.WEST);
        if (right != null) {
            p.add(right, BorderLayout.EAST);
        }
        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0));
        return capped(p);
    }

    /** Big name, a muted line under it and the item's buttons. */
    public static JComponent header(String title, String subtitle, JComponent actions) {
        JLabel name = new JLabel(title);
        name.setFont(Theme.bold(20));
        name.setForeground(Theme.TEXT);
        JPanel titles = new JPanel(new BorderLayout(0, 4));
        titles.setOpaque(false);
        titles.add(name, BorderLayout.NORTH);
        if (subtitle != null && !subtitle.isBlank()) {
            JLabel sub = Ui.muted(subtitle);
            sub.setFont(Theme.font(13));
            titles.add(sub, BorderLayout.CENTER);
        }
        JPanel h = new JPanel(new BorderLayout(0, 14));
        h.setOpaque(false);
        h.add(titles, BorderLayout.NORTH);
        if (actions != null) {
            h.add(actions, BorderLayout.CENTER);
        }
        h.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        return capped(h);
    }

    /** A list row inside a detail panel: two lines on the left, value(s) on the right, a hairline below. */
    public static JPanel line(String title, String sub, JComponent right) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 0, 8, 0)));
        JLabel l1 = new JLabel(title);
        l1.setFont(Theme.bold(13));
        l1.setForeground(Theme.TEXT);
        JPanel left = new JPanel(new BorderLayout(0, 2));
        left.setOpaque(false);
        left.add(l1, BorderLayout.NORTH);
        if (sub != null && !sub.isBlank()) {
            JLabel l2 = Ui.muted(sub);
            l2.setFont(Theme.font(12));
            left.add(l2, BorderLayout.CENTER);
        }
        r.add(left, BorderLayout.CENTER);
        if (right != null) {
            r.add(right, BorderLayout.EAST);
        }
        return capped(r);
    }

    /** Keeps a component at its natural height inside the vertical panel. */
    public static <T extends JComponent> T capped(T c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }
}
