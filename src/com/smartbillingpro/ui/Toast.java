package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Short, non-blocking confirmation message shown at the bottom of the window. */
public final class Toast {

    private Toast() {
    }

    public static void show(Component anywhere, String message, Ui.Tone tone) {
        JRootPane root = anywhere == null ? null : SwingUtilities.getRootPane(anywhere);
        if (root == null) {
            return;
        }
        JLayeredPane layers = root.getLayeredPane();
        String iconName = tone == Ui.Tone.GOOD ? "check" : tone == Ui.Tone.BAD ? "warning" : "info";
        java.awt.Color iconColor = tone == Ui.Tone.BAD ? new java.awt.Color(0xFFA8AD)
                : tone == Ui.Tone.GOOD ? Theme.ACCENT : java.awt.Color.WHITE;
        JLabel label = new JLabel(message, Icons.get(iconName, 18, iconColor), JLabel.LEFT) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.TEXT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setForeground(java.awt.Color.WHITE);
        label.setFont(Theme.bold(13));
        label.setIconTextGap(10);
        label.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 22));
        Dimension d = label.getPreferredSize();
        int w = Math.min(d.width, layers.getWidth() - 40);
        label.setBounds((layers.getWidth() - w) / 2, layers.getHeight() - d.height - 28, w, d.height);
        for (Component c : layers.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (c instanceof JComponent jc && Boolean.TRUE.equals(jc.getClientProperty("toast"))) {
                layers.remove(c);
            }
        }
        label.putClientProperty("toast", true);
        layers.add(label, JLayeredPane.POPUP_LAYER);
        layers.repaint();
        Timer t = new Timer(tone == Ui.Tone.BAD ? 4500 : 2800, e -> {
            layers.remove(label);
            layers.repaint();
        });
        t.setRepeats(false);
        t.start();
    }

    public static void ok(Component c, String message) {
        show(c, message, Ui.Tone.GOOD);
    }
}
