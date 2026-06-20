package com.smartbillingpro.ui.components;

import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** A JTable with comfortable spacing, subtle zebra striping and a hover highlight. */
public class StripedTable extends JTable {

    private int hoverRow = -1;

    public StripedTable(TableModel model) {
        super(model);
        setRowHeight(34);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setFillsViewportHeight(true);
        setFont(Theme.regular(13));
        setForeground(Theme.TEXT);
        setBackground(Theme.CARD);
        setSelectionBackground(Theme.withAlpha(Theme.PRIMARY, 45));
        setSelectionForeground(Theme.TEXT);
        setGridColor(Theme.CARD_BORDER);
        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setFont(Theme.bold(12));
        getTableHeader().setBackground(Theme.CARD);
        getTableHeader().setForeground(Theme.MUTED);
        getTableHeader().setPreferredSize(new Dimension(0, 38));

        // Track the row under the mouse so we can paint a gentle hover highlight.
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row != hoverRow) {
                    hoverRow = row;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverRow != -1) {
                    hoverRow = -1;
                    repaint();
                }
            }
        };
        addMouseMotionListener(hover);
        addMouseListener(hover);
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        if (isRowSelected(row)) {
            component.setBackground(getSelectionBackground());
        } else if (row == hoverRow) {
            component.setBackground(Theme.withAlpha(Theme.PRIMARY, 20));
        } else {
            component.setBackground(row % 2 == 0 ? Theme.CARD : Theme.TABLE_STRIPE);
        }
        if (component instanceof JComponent) {
            ((JComponent) component).setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        }
        return component;
    }
}
