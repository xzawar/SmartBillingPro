package com.smartbillingpro.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartbillingpro.service.Cart;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The items in the current sale. Every line has its own − / + buttons, the
 * amount and a remove button, so nothing is hidden in a toolbar.
 */
public final class CartList extends JPanel {

    /** What the page does when the cashier uses a line's controls. */
    public interface Actions {
        void step(Cart.Line line, int units);

        void remove(Cart.Line line);

        void edit(Cart.Line line);

        void select(Cart.Line line);
    }

    private final ScrollablePanel rows = new ScrollablePanel(null);
    private final JScrollPane scroll;
    private final CardLayout cards = new CardLayout();
    private final Actions actions;

    public CartList(Actions actions) {
        this.actions = actions;
        setLayout(cards);
        setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(true);
        rows.setBackground(Theme.SURFACE);
        scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(new EmptyState("barcode", "No items yet", "Scan a barcode, or tap a product on the left to add it.", true),
                "empty");
        add(scroll, "list");
    }

    public void setLines(List<Cart.Line> lines, Cart.Line selected) {
        rows.removeAll();
        LineRow selectedRow = null;
        for (Cart.Line l : lines) {
            LineRow r = new LineRow(l, l == selected);
            rows.add(r);
            if (l == selected) {
                selectedRow = r;
            }
        }
        rows.add(javax.swing.Box.createVerticalGlue());
        cards.show(this, lines.isEmpty() ? "empty" : "list");
        rows.revalidate();
        rows.repaint();
        if (selectedRow != null) {
            final LineRow target = selectedRow;
            SwingUtilities.invokeLater(() -> target.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, target.getHeight())));
        }
    }

    private static JButton round(String icon, String tip) {
        JButton b = new JButton(Icons.get(icon, 14, Theme.ACCENT_INK));
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:" + Theme.hex(Theme.NEUTRAL_FILL)
                + ";hoverBackground:#e2e4da;pressedBackground:#d8dacf;borderWidth:0;focusWidth:0;margin:5,5,5,5");
        b.setFocusable(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private final class LineRow extends JPanel {
        private final boolean selected;
        private boolean hover;

        LineRow(Cart.Line line, boolean selected) {
            super(new BorderLayout(10, 0));
            this.selected = selected;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 8));
            String unit = line.item().unitCode();
            JLabel name = new JLabel(line.item().displayName());
            name.setFont(Theme.bold(14));
            name.setForeground(Theme.TEXT);
            String detail = line.item().fractional()
                    ? Qty.format(line.qty(), unit) + " × " + Money.format(line.unitPrice()) + " / " + unit
                    : Money.format(line.unitPrice()) + " each";
            if (line.lineDiscount() > 0) {
                detail += "  •  " + Money.format(line.lineDiscount()) + " off";
            }
            JLabel sub = new JLabel(detail);
            sub.setFont(Theme.font(12));
            sub.setForeground(line.lineDiscount() > 0 ? Theme.GOOD : Theme.MUTED);
            JPanel text = new JPanel(new BorderLayout(0, 2));
            text.setOpaque(false);
            text.add(name, BorderLayout.NORTH);
            text.add(sub, BorderLayout.CENTER);
            text.setMinimumSize(new Dimension(40, 10));

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            right.setOpaque(false);
            if (line.item().fractional()) {
                JButton qty = new JButton(Qty.format(line.qty(), unit));
                qty.setToolTipText("Change the weight or amount");
                qty.putClientProperty(FlatClientProperties.STYLE, "arc:999;background:" + Theme.hex(Theme.NEUTRAL_FILL)
                        + ";borderWidth:0;focusWidth:0;margin:4,10,4,10");
                qty.setFont(Theme.bold(13));
                qty.setFocusable(false);
                qty.addActionListener(e -> actions.edit(line));
                right.add(qty);
            } else {
                JButton minus = round("minus", "One less");
                minus.addActionListener(e -> actions.step(line, -1));
                JLabel q = new JLabel(Qty.format(line.qty()));
                q.setFont(Theme.bold(14));
                q.setHorizontalAlignment(SwingConstants.CENTER);
                q.setPreferredSize(new Dimension(30, 26));
                JButton plus = round("plus", "One more");
                plus.addActionListener(e -> actions.step(line, 1));
                right.add(minus);
                right.add(q);
                right.add(plus);
            }
            JLabel amount = new JLabel(Money.grouped(line.netBeforeCartDiscount()));
            amount.setFont(Theme.bold(14));
            amount.setForeground(Theme.TEXT);
            amount.setHorizontalAlignment(SwingConstants.RIGHT);
            amount.setPreferredSize(new Dimension(82, 26));
            JButton remove = Ui.icon("close", "Remove this item");
            remove.setFocusable(false);
            remove.addActionListener(e -> actions.remove(line));
            right.add(amount);
            right.add(remove);

            add(text, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        actions.edit(line);
                    } else {
                        actions.select(line);
                    }
                }

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
            };
            addMouseListener(m);
            text.addMouseListener(m);
            name.addMouseListener(m);
            sub.addMouseListener(m);
            setToolTipText("Double-click to change quantity, price or discount");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected || hover) {
                g2.setColor(selected ? Theme.ACCENT_SOFT : Theme.SURFACE_ALT);
                g2.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 22, 22);
            } else {
                g2.setColor(Theme.BORDER);
                g2.drawLine(12, getHeight() - 1, getWidth() - 12, getHeight() - 1);
            }
            g2.dispose();
        }
    }
}
