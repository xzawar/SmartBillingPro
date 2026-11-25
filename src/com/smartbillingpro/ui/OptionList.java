package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Big, easy choices for a single question ("How do you sell it?"). Click or
 * press Enter on an option to choose it; arrow keys move between options.
 */
public final class OptionList extends JPanel {

    public record Option(String key, String icon, String title, String subtitle) {
    }

    private final List<Row> rows = new ArrayList<>();
    private Consumer<String> onPick = k -> { };
    private String selected;

    public OptionList() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    public OptionList(List<Option> options) {
        this();
        setOptions(options);
    }

    public void setOptions(List<Option> options) {
        removeAll();
        rows.clear();
        for (int i = 0; i < options.size(); i++) {
            Row r = new Row(options.get(i));
            rows.add(r);
            add(r);
            if (i < options.size() - 1) {
                add(Box.createVerticalStrut(8));
            }
        }
        revalidate();
        repaint();
    }

    public void onPick(Consumer<String> c) {
        this.onPick = c;
    }

    public String selected() {
        return selected;
    }

    public void setSelected(String key) {
        selected = key;
        repaint();
    }

    public void focusSelectedOrFirst() {
        for (Row r : rows) {
            if (Objects.equals(r.option.key(), selected)) {
                r.requestFocusInWindow();
                return;
            }
        }
        if (!rows.isEmpty()) {
            rows.get(0).requestFocusInWindow();
        }
    }

    private void moveFocus(Row from, int delta) {
        int i = rows.indexOf(from) + delta;
        if (i >= 0 && i < rows.size()) {
            rows.get(i).requestFocusInWindow();
            rows.get(i).scrollRectToVisible(rows.get(i).getBounds());
        }
    }

    private final class Row extends JPanel {
        private final Option option;
        private boolean hover;

        Row(Option option) {
            super(new BorderLayout(14, 0));
            this.option = option;
            setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 16));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel icon = new JLabel(option.icon() == null ? null : Icons.get(option.icon(), 20, Theme.ACCENT_INK)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isSelected() ? Theme.ACCENT : Theme.NEUTRAL_SOFT);
                    g2.fillOval(0, 0, getWidth(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            icon.setHorizontalAlignment(JLabel.CENTER);
            icon.setPreferredSize(new Dimension(40, 40));
            JLabel title = new JLabel(option.title());
            title.setFont(Theme.bold(15));
            title.setForeground(Theme.TEXT);
            JPanel text = new JPanel(new BorderLayout(0, 2));
            text.setOpaque(false);
            text.add(title, BorderLayout.NORTH);
            if (option.subtitle() != null && !option.subtitle().isBlank()) {
                JLabel sub = new JLabel(option.subtitle());
                sub.setFont(Theme.font(13));
                sub.setForeground(Theme.MUTED);
                text.add(sub, BorderLayout.CENTER);
            }
            JPanel middle = new JPanel(new java.awt.GridBagLayout());
            middle.setOpaque(false);
            java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
            c.weightx = 1;
            c.fill = java.awt.GridBagConstraints.HORIZONTAL;
            middle.add(text, c);
            JLabel chevron = new JLabel(Icons.get("chevron-right", 18, Theme.MUTED));
            if (option.icon() != null) {
                add(icon, BorderLayout.WEST);
            }
            add(middle, BorderLayout.CENTER);
            add(chevron, BorderLayout.EAST);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    pick();
                }
            };
            addMouseListener(m);
            for (Component child : new Component[] {icon, title, text, middle, chevron}) {
                child.addMouseListener(m);
            }
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
            bind(KeyEvent.VK_ENTER, this::pick);
            bind(KeyEvent.VK_SPACE, this::pick);
            bind(KeyEvent.VK_DOWN, () -> moveFocus(this, 1));
            bind(KeyEvent.VK_UP, () -> moveFocus(this, -1));
        }

        private void bind(int key, Runnable r) {
            String id = "opt" + key;
            getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), id);
            getActionMap().put(id, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    r.run();
                }
            });
        }

        private boolean isSelected() {
            return Objects.equals(selected, option.key());
        }

        private void pick() {
            selected = option.key();
            OptionList.this.repaint();
            onPick.accept(option.key());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean sel = isSelected();
            Color fill = sel ? Theme.ACCENT_SOFT : hover ? Theme.SURFACE_ALT : Theme.SURFACE;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 32, 32);
            g2.setColor(sel || isFocusOwner() ? Theme.ACCENT_INK : Theme.BORDER);
            g2.setStroke(new java.awt.BasicStroke(sel || isFocusOwner() ? 1.6f : 1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 32, 32);
            g2.dispose();
        }
    }
}
