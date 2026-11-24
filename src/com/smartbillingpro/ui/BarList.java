package com.smartbillingpro.ui;

import com.smartbillingpro.util.Money;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Ranked horizontal bars (top products, categories) with name and value labels. */
public final class BarList extends JComponent {

    public record Item(String label, long value, String detail) {
    }

    private List<Item> items = new ArrayList<>();
    private final String emptyText;
    private static final int ROW = 30;

    public BarList(String emptyText) {
        this.emptyText = emptyText;
        setPreferredSize(new Dimension(360, 240));
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setItems(List<Item> items) {
        this.items = items;
        revalidate();
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = (e.getY() - 6) / ROW;
        if (i < 0 || i >= items.size()) {
            return null;
        }
        Item it = items.get(i);
        return "<html><b>" + it.label() + "</b><br>" + Money.format(it.value())
                + (it.detail() == null ? "" : "<br>" + it.detail()) + "</html>";
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        if (items.isEmpty()) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.font(13));
            int tw = g2.getFontMetrics().stringWidth(emptyText);
            g2.drawString(emptyText, (w - tw) / 2, getHeight() / 2);
            g2.dispose();
            return;
        }
        long max = items.stream().mapToLong(Item::value).max().orElse(1);
        g2.setFont(Theme.font(12));
        FontMetrics fm = g2.getFontMetrics();
        int labelW = Math.min(170, Math.max(90, w * 34 / 100));
        int valueW = 74;
        int barX = labelW + 8;
        int barMax = Math.max(20, w - barX - valueW - 6);
        int y = 6;
        for (Item it : items) {
            String label = it.label();
            while (fm.stringWidth(label) > labelW - 4 && label.length() > 2) {
                label = label.substring(0, label.length() - 2);
            }
            if (!label.equals(it.label())) {
                label = label + "…";
            }
            g2.setColor(Theme.TEXT_2);
            g2.drawString(label, 0, y + 18);
            int bw = (int) Math.max(2, barMax * (double) it.value() / Math.max(1, max));
            g2.setColor(Theme.SERIES_1);
            g2.fill(new RoundRectangle2D.Double(barX, y + 7, bw, 14, 4, 4));
            g2.fillRect(barX, y + 7, Math.min(3, bw), 14);
            g2.setColor(Theme.TEXT);
            String v = ColumnChart.compact(it.value());
            g2.drawString(v, barX + bw + 6, y + 18);
            y += ROW;
        }
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, Math.max(200, items.size() * ROW + 12));
    }
}
