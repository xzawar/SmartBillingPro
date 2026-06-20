package com.smartbillingpro.ui.components;

import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

/** A dashboard metric card: a coloured accent bar, a title and a big value. */
public class StatCard extends RoundedPanel {

    private final JLabel valueLabel = new JLabel("0");
    private final JLabel titleLabel = new JLabel("Title");
    private final JLabel hintLabel = new JLabel(" ");

    public StatCard(String title, Color accent) {
        super(new BorderLayout(12, 0));
        setArc(18);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel accentBar = new JPanel();
        accentBar.setBackground(accent);
        accentBar.setPreferredSize(new Dimension(6, 10));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        titleLabel.setText(title.toUpperCase());
        titleLabel.setFont(Theme.bold(11));
        titleLabel.setForeground(Theme.MUTED);

        valueLabel.setFont(Theme.bold(24));
        valueLabel.setForeground(Theme.TEXT);

        hintLabel.setFont(Theme.regular(11));
        hintLabel.setForeground(Theme.MUTED);

        text.add(titleLabel);
        text.add(valueLabel);
        text.add(hintLabel);

        add(accentBar, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setHint(String hint) {
        hintLabel.setText(hint == null || hint.isEmpty() ? " " : hint);
    }
}
