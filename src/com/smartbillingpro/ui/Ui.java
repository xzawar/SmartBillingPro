package com.smartbillingpro.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartbillingpro.db.AppException;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;

/** Shared building blocks so every screen looks and behaves the same. */
public final class Ui {

    /** Meaning of a status colour. Always shown with text. */
    public enum Tone {
        GOOD(Theme.GOOD, Theme.GOOD_SOFT), WARN(Theme.WARN, Theme.WARN_SOFT), BAD(Theme.BAD, Theme.BAD_SOFT),
        INFO(Theme.INFO, Theme.INFO_SOFT), NEUTRAL(Theme.TEXT_2, Theme.NEUTRAL_SOFT);

        public final Color fg;
        public final Color bg;

        Tone(Color fg, Color bg) {
            this.fg = fg;
            this.bg = bg;
        }
    }

    private Ui() {
    }

    // ------------------------------------------------------------ buttons

    private static JButton button(String text, String icon, Color fg) {
        JButton b = new JButton(text);
        if (icon != null) {
            b.setIcon(Icons.get(icon, 16, fg));
            b.setIconTextGap(7);
        }
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(true);
        return b;
    }

    /** The one prominent action of a screen or dialog: lime pill with deep ink text. */
    public static JButton primary(String text, String icon) {
        JButton b = button(text, icon, Theme.ACCENT_INK);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:" + Theme.hex(Theme.ACCENT)
                + ";foreground:" + Theme.hex(Theme.ACCENT_INK) + ";hoverBackground:" + Theme.hex(Theme.ACCENT_HOVER)
                + ";pressedBackground:" + Theme.hex(Theme.ACCENT_PRESSED) + ";borderWidth:0;focusWidth:0;"
                + "disabledBackground:#eeefe9;disabledText:#9a9c96;margin:8,20,8,20");
        b.setFont(Theme.bold(14));
        return b;
    }

    /** The large confirm action of the till (Complete Sale). */
    public static JButton success(String text, String icon) {
        JButton b = button(text, icon, Theme.ACCENT_INK);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:" + Theme.hex(Theme.ACCENT)
                + ";foreground:" + Theme.hex(Theme.ACCENT_INK) + ";hoverBackground:" + Theme.hex(Theme.ACCENT_HOVER)
                + ";pressedBackground:" + Theme.hex(Theme.ACCENT_PRESSED) + ";borderWidth:0;focusWidth:0;"
                + "disabledBackground:#eeefe9;disabledText:#9a9c96;margin:14,20,14,20");
        b.setFont(Theme.bold(17));
        return b;
    }

    /** Quiet pill on a neutral tint. */
    public static JButton secondary(String text, String icon) {
        JButton b = button(text, icon, Theme.ACCENT_INK);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:" + Theme.hex(Theme.NEUTRAL_FILL)
                + ";foreground:" + Theme.hex(Theme.ACCENT_INK) + ";hoverBackground:#e2e4da;pressedBackground:#d8dacf;"
                + "borderWidth:0;focusWidth:0;disabledText:#a3a59f;margin:8,16,8,16");
        b.setFont(Theme.bold(13));
        return b;
    }

    /** Text-only action. */
    public static JButton ghost(String text, String icon) {
        JButton b = button(text, icon, Theme.TEXT_2);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:999;foreground:" + Theme.hex(Theme.TEXT_2)
                + ";hoverBackground:" + Theme.hex(Theme.NAV_HOVER) + ";margin:7,12,7,12");
        b.setFont(Theme.bold(13));
        return b;
    }

    /** Destructive action (negative secondary). */
    public static JButton danger(String text, String icon) {
        JButton b = button(text, icon, Theme.BAD);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:" + Theme.hex(Theme.BAD_SOFT)
                + ";foreground:" + Theme.hex(Theme.BAD) + ";hoverBackground:#f6d9da;pressedBackground:#f0c9ca;"
                + "borderWidth:0;focusWidth:0;margin:8,16,8,16");
        b.setFont(Theme.bold(13));
        return b;
    }

    public static JButton icon(String icon, String tooltip) {
        JButton b = new JButton(Icons.get(icon, 16, Theme.TEXT_2));
        b.setToolTipText(tooltip);
        b.getAccessibleContext().setAccessibleName(tooltip);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ------------------------------------------------------------ labels

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(26));
        l.setForeground(Theme.TEXT);
        return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(16));
        l.setForeground(Theme.TEXT);
        return l;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(13));
        l.setForeground(Theme.TEXT_2);
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(13));
        l.setForeground(Theme.MUTED);
        return l;
    }

    /** A coloured pill with a text label (status is never colour-only). */
    public static JLabel pill(String text, Tone tone) {
        JLabel l = new JLabel(text) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        setTone(l, tone);
        l.setFont(Theme.bold(11));
        l.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        l.setOpaque(false);
        return l;
    }

    public static void setTone(JLabel pill, Tone tone) {
        pill.setForeground(tone.fg);
        pill.setBackground(tone.bg);
        pill.repaint();
    }

    // ------------------------------------------------------------ fields

    public static JTextField field() {
        return new JTextField(14);
    }

    public static JTextField field(String placeholder) {
        JTextField f = new JTextField(14);
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        return f;
    }

    public static JTextField search(String placeholder) {
        JTextField f = field(placeholder);
        f.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.get("search", 16, Theme.MUTED));
        f.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        return f;
    }

    /** Right-aligned amount field. */
    public static JTextField money(String placeholder) {
        JTextField f = field(placeholder);
        f.setHorizontalAlignment(SwingConstants.RIGHT);
        f.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, symbolLabel());
        return f;
    }

    private static JLabel symbolLabel() {
        JLabel l = new JLabel(Money.symbol());
        l.setForeground(Theme.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 2));
        return l;
    }

    public static void setError(JComponent field, boolean error) {
        field.putClientProperty(FlatClientProperties.OUTLINE, error ? FlatClientProperties.OUTLINE_ERROR : null);
    }

    /** Parse an amount; marks the field and throws a readable error when invalid. */
    public static long moneyValue(JTextField f, String name, boolean required) {
        String t = f.getText().trim();
        if (t.isEmpty()) {
            if (required) {
                setError(f, true);
                f.requestFocusInWindow();
                throw new AppException("Please enter " + name + ".");
            }
            setError(f, false);
            return 0;
        }
        try {
            long v = Money.parse(t);
            if (v < 0) {
                throw new IllegalArgumentException(name + " cannot be negative.");
            }
            setError(f, false);
            return v;
        } catch (IllegalArgumentException e) {
            setError(f, true);
            f.requestFocusInWindow();
            throw new AppException(capitalize(name) + ": " + e.getMessage());
        }
    }

    /** Like {@link #moneyValue} but allows a leading minus sign (e.g. an advance paid). Empty means 0. */
    public static long signedMoneyValue(JTextField f, String name) {
        String t = f.getText().trim();
        if (t.isEmpty()) {
            setError(f, false);
            return 0;
        }
        boolean negative = t.startsWith("-");
        try {
            long v = Money.parse(negative ? t.substring(1).trim() : t);
            setError(f, false);
            return negative ? -v : v;
        } catch (IllegalArgumentException e) {
            setError(f, true);
            f.requestFocusInWindow();
            throw new AppException(capitalize(name) + ": " + e.getMessage());
        }
    }

    public static long qtyValue(JTextField f, String name, boolean required) {
        String t = f.getText().trim();
        if (t.isEmpty()) {
            if (required) {
                setError(f, true);
                f.requestFocusInWindow();
                throw new AppException("Please enter " + name + ".");
            }
            return 0;
        }
        try {
            long v = Qty.parse(t);
            if (v < 0) {
                throw new IllegalArgumentException("cannot be negative.");
            }
            setError(f, false);
            return v;
        } catch (IllegalArgumentException e) {
            setError(f, true);
            f.requestFocusInWindow();
            throw new AppException(capitalize(name) + ": " + e.getMessage());
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void onChange(JTextComponent f, Runnable r) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        });
    }

    // ------------------------------------------------------------ layout

    /** Label above field. The field keeps its natural height even in a grid. */
    public static JPanel row(String label, JComponent field) {
        return row(label, field, null);
    }

    public static JPanel row(String label, JComponent field, String hint) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        p.add(label(label), BorderLayout.NORTH);
        JPanel holder = new JPanel(new BorderLayout(0, 3));
        holder.setOpaque(false);
        if (field instanceof JScrollPane sp && sp.getBorder() instanceof javax.swing.border.EmptyBorder) {
            sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER_STRONG));
        }
        holder.add(field, BorderLayout.NORTH);
        if (hint != null) {
            JLabel h = muted(hint);
            h.setFont(Theme.font(11));
            h.setVerticalAlignment(SwingConstants.TOP);
            holder.add(h, BorderLayout.CENTER);
        }
        p.add(holder, BorderLayout.CENTER);
        return p;
    }

    /** A toolbar row: items on the left at their natural size, optional items on the right. */
    public static JPanel toolbar(Component[] left, Component... right) {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        JPanel l = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        l.setOpaque(false);
        for (int i = 0; i < left.length; i++) {
            if (i > 0) {
                l.add(hgap(12));
            }
            l.add(left[i]);
        }
        bar.add(l, BorderLayout.WEST);
        if (right.length > 0) {
            bar.add(flowRight(8, right), BorderLayout.EAST);
        }
        return bar;
    }

    public static JPanel flowLeft(int gap, Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        for (Component c : parts) {
            p.add(c);
        }
        return p;
    }

    public static JPanel flowRight(int gap, Component... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, gap, 0));
        p.setOpaque(false);
        for (Component c : parts) {
            p.add(c);
        }
        return p;
    }

    public static JPanel vbox(int gap, Component... parts) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            p.add(parts[i]);
            if (i < parts.length - 1 && gap > 0) {
                p.add(Box.createVerticalStrut(gap));
            }
        }
        return p;
    }

    /** A 2-column form grid that collapses nicely. */
    public static JPanel grid(int cols, int hgap, int vgap, Component... parts) {
        JPanel p = new JPanel(new java.awt.GridLayout(0, cols, hgap, vgap));
        p.setOpaque(false);
        for (Component c : parts) {
            p.add(c);
        }
        return p;
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getHorizontalScrollBar().setUnitIncrement(18);
        sp.getViewport().setBackground(Theme.SURFACE);
        return sp;
    }

    public static Card card(JComponent content, int pad) {
        Card c = new Card(new BorderLayout());
        c.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
        c.add(content, BorderLayout.CENTER);
        return c;
    }

    public static Component hgap(int w) {
        return Box.createRigidArea(new Dimension(w, 0));
    }

    // ------------------------------------------------------------ keys

    /** Bind a keyboard shortcut that works anywhere inside {@code root}'s window. */
    public static void key(JComponent root, String stroke, Runnable action) {
        KeyStroke ks = KeyStroke.getKeyStroke(stroke);
        if (ks == null) {
            throw new IllegalArgumentException("Bad key stroke " + stroke);
        }
        String id = "key:" + stroke;
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, id);
        root.getActionMap().put(id, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    public static void onEnter(JTextField f, Runnable r) {
        f.addActionListener(e -> r.run());
    }
}
