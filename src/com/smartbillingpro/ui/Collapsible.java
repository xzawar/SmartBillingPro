package com.smartbillingpro.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/** "More details" section that stays out of the way until it is needed. */
public final class Collapsible extends JPanel {

    private final JButton toggle;
    private final JComponent content;
    private final String label;

    public Collapsible(String label, JComponent content) {
        super(new BorderLayout(0, 10));
        this.label = label;
        this.content = content;
        setOpaque(false);
        toggle = Ui.ghost(label, "chevron-right");
        toggle.addActionListener(e -> setExpanded(!content.isVisible()));
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        head.setOpaque(false);
        head.add(toggle);
        add(head, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        content.setVisible(false);
    }

    public void setExpanded(boolean open) {
        content.setVisible(open);
        toggle.setIcon(Icons.get(open ? "chevron-down" : "chevron-right", 16, Theme.TEXT_2));
        toggle.setText(open ? label.replaceFirst("^(Show|Add)", "Hide") : label);
        revalidate();
        repaint();
    }

    public boolean expanded() {
        return content.isVisible();
    }
}
