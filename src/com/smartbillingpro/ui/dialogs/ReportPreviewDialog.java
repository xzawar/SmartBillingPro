package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Settings;
import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
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
 * Shows any business report (inventory, sales or profit) in one consistent,
 * branded format – a gradient header with the shop name and report title, a
 * clean white card with the monospaced report body, and a Print button so the
 * exact same layout can be printed.
 */
public class ReportPreviewDialog extends JDialog {

    private final JTextArea printArea = new JTextArea();

    public ReportPreviewDialog(Window owner, Settings settings, String reportTitle, String content) {
        super(owner, reportTitle, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);

        // ---------- header ----------
        GradientPanel header = new GradientPanel(new BorderLayout());
        header.setColors(Theme.PRIMARY, Theme.ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        JPanel headLeft = new JPanel();
        headLeft.setOpaque(false);
        headLeft.setLayout(new BoxLayout(headLeft, BoxLayout.Y_AXIS));
        JLabel biz = new JLabel(settings.getBusinessName());
        biz.setFont(Theme.bold(20));
        biz.setForeground(Color.WHITE);
        JLabel title = new JLabel(reportTitle);
        title.setFont(Theme.regular(13));
        title.setForeground(new Color(235, 238, 255));
        headLeft.add(biz);
        headLeft.add(title);
        header.add(headLeft, BorderLayout.WEST);
        JLabel tag = new JLabel("REPORT");
        tag.setFont(Theme.bold(22));
        tag.setForeground(Color.WHITE);
        header.add(tag, BorderLayout.EAST);

        // ---------- body ----------
        printArea.setEditable(false);
        printArea.setText(content);
        printArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        printArea.setForeground(Theme.TEXT);
        printArea.setBackground(Color.WHITE);
        printArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        printArea.setCaretPosition(0);

        RoundedPanel card = new RoundedPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        card.add(Ui.scroll(printArea), BorderLayout.CENTER);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setBackground(Theme.BG);
        bodyWrap.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 18));
        bodyWrap.add(card, BorderLayout.CENTER);

        // ---------- buttons ----------
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setBackground(Theme.BG);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 18, 14, 18));
        RoundedButton close = new RoundedButton("Close", Theme.withAlpha(Theme.MUTED, 40), Theme.TEXT);
        close.addActionListener(e -> dispose());
        RoundedButton print = new RoundedButton("Print", Theme.PRIMARY, Color.WHITE);
        print.addActionListener(e -> doPrint());
        buttons.add(close);
        buttons.add(print);

        root.add(header, BorderLayout.NORTH);
        root.add(bodyWrap, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(new Dimension(760, 720));
        setLocationRelativeTo(owner);
    }

    private void doPrint() {
        try {
            printArea.print();
        } catch (PrinterException ex) {
            JOptionPane.showMessageDialog(this, "Could not print: " + ex.getMessage(),
                    "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
