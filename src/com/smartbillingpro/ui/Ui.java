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
        INFO(Theme.PRIMARY, Theme.PRIMARY_SOFT), NEUTRAL(Theme.TEXT_2, new Color(0xEEF1F5));

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

    /** The one prominent action of a screen or dialog. */
    public static JButton primary(String text, String icon) {
        JButton b = button(text, icon, Color.WHITE);
        b.putClientProperty(FlatClientProperties.STYLE, "background:" + Theme.hex(Theme.PRIMARY)
                + ";foreground:#ffffff;hoverBackground:" + Theme.hex(Theme.PRIMARY_HOVER)
                + ";pressedBackground:#17459a;borderWidth:0;focusWidth:0;font:bold;margin:7,16,7,16");
        return b;
    }

    /** Large green confirm button (Complete Sale). */
    public static JButton success(String text, String icon) {
        JButton b = button(text, icon, Color.WHITE);
        b.putClientProperty(FlatClientProperties.STYLE, "background:#15803d;foreground:#ffffff;hoverBackground:#126b33;"
                + "pressedBackground:#0f5a2b;borderWidth:0;focusWidth:0;font:bold +4;arc:10;margin:12,18,12,18");
        return b;
    }

    public static JButton secondary(String text, String icon) {
        JButton b = button(text, icon, Theme.TEXT_2);
        b.putClientProperty(FlatClientProperties.STYLE, "background:#ffffff;foreground:" + Theme.hex(Theme.TEXT)
                + ";borderColor:" + Theme.hex(Theme.BORDER_STRONG) + ";hoverBackground:#f3f5f8;focusWidth:0;margin:7,14,7,14");
        return b;
    }

    public static JButton ghost(String text, String icon) {
        JButton b = button(text, icon, Theme.TEXT_2);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        b.putClientProperty(FlatClientProperties.STYLE, "foreground:" + Theme.hex(Theme.TEXT_2) + ";margin:6,10,6,10");
        return b;
    }

    public static JButton danger(String text, String icon) {
        JButton b = button(text, icon, Theme.BAD);
        b.putClientProperty(FlatClientProperties.STYLE, "background:#ffffff;foreground:" + Theme.hex(Theme.BAD)
                + ";borderColor:#f0b4b4;hoverBackground:" + Theme.hex(Theme.BAD_SOFT) + ";focusWidth:0;margin:7,14,7,14");
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
        l.setFont(Theme.bold(22));
        l.setForeground(Theme.TEXT);
        return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(15));
        l.setForeground(Theme.TEXT);
        return l;
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.bold(12));
        l.setForeground(Theme.TEXT_2);
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(12));
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
        l.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
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

    /** Label above field. */
    public static JPanel row(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        p.add(label(label), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    public static JPanel row(String label, JComponent field, String hint) {
        JPanel p = row(label, field);
        JLabel h = muted(hint);
        h.setFont(Theme.font(11));
        p.add(h, BorderLayout.SOUTH);
        return p;
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
