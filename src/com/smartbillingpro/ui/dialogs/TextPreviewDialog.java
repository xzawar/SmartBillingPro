package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.print.PrinterException;

/**
 * Shows monospaced text (a receipt or report) with a Print button. Printing
 * uses the standard Java print dialog so any installed printer works, including
 * thermal receipt printers.
 */
public class TextPreviewDialog extends JDialog {

    private final JTextArea textArea = new JTextArea();

    public TextPreviewDialog(Window owner, String title, String content, int columns) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);

        GradientPanel header = new GradientPanel(new BorderLayout());
        header.setColors(Theme.PRIMARY, Theme.ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.bold(16));
        heading.setForeground(Color.WHITE);
        header.add(heading, BorderLayout.WEST);

        textArea.setText(content);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setBackground(Color.WHITE);
        textArea.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        textArea.setColumns(columns);
        textArea.setCaretPosition(0);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.BG);
        center.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));
        center.add(Ui.scroll(textArea), BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setBackground(Theme.BG);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));
        RoundedButton close = new RoundedButton("Close", Theme.withAlpha(Theme.MUTED, 40), Theme.TEXT);
        close.addActionListener(e -> dispose());
        RoundedButton print = new RoundedButton("Print", Theme.PRIMARY, Color.WHITE);
        print.addActionListener(e -> print());
        footer.add(close);
        footer.add(print);

        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(new Dimension(columns <= 40 ? 420 : 720, 620));
        setLocationRelativeTo(owner);
    }

    private void print() {
        try {
            boolean done = textArea.print();
            if (!done) {
                return;
            }
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(this, "Could not print: " + ex.getMessage(),
                    "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
