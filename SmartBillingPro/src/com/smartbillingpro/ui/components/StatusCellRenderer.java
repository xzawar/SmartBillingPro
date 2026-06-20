package com.smartbillingpro.ui.components;

import com.smartbillingpro.ui.theme.Theme;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

/** Colours common status words (stock levels, invoice states) for quick scanning. */
public class StatusCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        Component component = super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);
        setFont(Theme.bold(12));
        if (!isSelected) {
            setForeground(colorFor(String.valueOf(value)));
        }
        return component;
    }

    private Color colorFor(String value) {
        String text = value == null ? "" : value.toLowerCase();
        if (text.contains("out")) {
            return Theme.DANGER;
        }
        if (text.contains("low")) {
            return Theme.WARNING;
        }
        if (text.contains("void")) {
            return Theme.DANGER;
        }
        if (text.contains("refund")) {
            return Theme.ACCENT;
        }
        if (text.contains("in stock") || text.contains("complete") || text.contains("received")) {
            return Theme.SUCCESS;
        }
        return Theme.MUTED;
    }
}
