package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/** Base for simple add/edit dialogs: a titled header, a field grid and Save/Cancel. */
public abstract class FormDialog extends JDialog {

    private final JPanel fields = new JPanel(new GridBagLayout());
    private final JLabel errorLabel = new JLabel(" ");
    private final RoundedButton saveButton;
    private boolean saved = false;
    private int rowIndex = 0;

    protected FormDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);

        GradientPanel header = new GradientPanel(new BorderLayout());
        header.setColors(Theme.PRIMARY, Theme.ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 22));
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.bold(18));
        heading.setForeground(Color.WHITE);
        header.add(heading, BorderLayout.WEST);

        fields.setOpaque(false);
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));
        card.add(fields, BorderLayout.CENTER);
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setFont(Theme.regular(12));
        card.add(errorLabel, BorderLayout.SOUTH);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG);
        wrap.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        wrap.add(card, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setBackground(Theme.BG);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        RoundedButton cancel = new RoundedButton("Cancel", Theme.withAlpha(Theme.MUTED, 40), Theme.TEXT);
        cancel.addActionListener(e -> dispose());
        saveButton = new RoundedButton("Save", Theme.PRIMARY, Color.WHITE);
        saveButton.addActionListener(e -> trySave());
        footer.add(cancel);
        footer.add(saveButton);

        root.add(header, BorderLayout.NORTH);
        root.add(wrap, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    protected void addField(String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = rowIndex;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(8, 0, 0, 12);
        fields.add(Ui.formLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 0, 0);
        fields.add(field, c);
        rowIndex++;
    }

    protected void setPrimaryActionText(String text) {
        saveButton.setText(text);
    }

    protected void error(String message) {
        errorLabel.setText(message == null ? " " : message);
    }

    /** Returns true to accept and close the dialog, false to keep it open. */
    protected abstract boolean onSave();

    private void trySave() {
        try {
            if (onSave()) {
                saved = true;
                dispose();
            }
        } catch (RuntimeException ex) {
            error(ex.getMessage());
        }
    }

    public boolean showDialog() {
        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return saved;
    }
}
