package com.smartbillingpro.ui.components;

import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.text.JTextComponent;
import java.awt.Component;

/** Small shared helpers for consistent fields, labels and scroll panes. */
public final class Ui {

    private Ui() {
    }

    /** Applies the standard rounded border and font to a text field. */
    public static void styleField(JTextComponent field) {
        field.setFont(Theme.regular(13));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(8, 11, 8, 11)));
    }

    public static void styleComponent(JComponent component) {
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    public static JLabel formLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.bold(12));
        label.setForeground(Theme.MUTED);
        return label;
    }

    public static JLabel title(String text, int size) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.bold(size));
        label.setForeground(Theme.TEXT);
        return label;
    }

    /** Wraps a component in a clean borderless scroll pane on a card background. */
    public static JScrollPane scroll(Component component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(Theme.CARD);
        pane.setBackground(Theme.CARD);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }
}
