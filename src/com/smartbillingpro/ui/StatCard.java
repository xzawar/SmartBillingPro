package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** A dashboard metric: label, big value and a hint line with an optional status pill. */
public class StatCard extends Card {

    private final JLabel value = new JLabel("—");
    private final JLabel hint = new JLabel(" ");
    private final JLabel status = Ui.pill("", Ui.Tone.NEUTRAL);
    private final JLabel iconLabel = new JLabel();

    public StatCard(String title, String icon) {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setPreferredSize(new Dimension(220, 112));
        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(12));
        t.setForeground(Theme.TEXT_2);
        iconLabel.setIcon(Icons.get(icon, 18, Theme.MUTED));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(t, BorderLayout.CENTER);
        top.add(iconLabel, BorderLayout.EAST);
        value.setFont(Theme.bold(24));
        value.setForeground(Theme.TEXT);
        hint.setFont(Theme.font(12));
        hint.setForeground(Theme.MUTED);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(hint, BorderLayout.CENTER);
        status.setVisible(false);
        bottom.add(status, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(value, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
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
        value.setForeground(tone == Ui.Tone.BAD ? Theme.BAD : tone == Ui.Tone.GOOD ? Theme.GOOD : Theme.TEXT);
    }
}
