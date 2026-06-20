package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.ui.components.GradientPanel;
import com.smartbillingpro.ui.components.RoundedButton;
import com.smartbillingpro.ui.components.RoundedPanel;
import com.smartbillingpro.ui.components.Ui;
import com.smartbillingpro.ui.panels.DefaultDocListener;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.Money;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.math.BigDecimal;

/** Collects the cash tendered and shows the change due before finishing a sale. */
public class PaymentDialog extends JDialog {

    private final BigDecimal total;
    private final String symbol;
    private final JTextField cashField = new JTextField();
    private final JLabel changeLabel = new JLabel();
    private final RoundedButton confirmButton;

    private boolean confirmed = false;
    private BigDecimal amountPaid = BigDecimal.ZERO;

    public PaymentDialog(Window owner, BigDecimal total, String symbol) {
        super(owner, "Take Payment", ModalityType.APPLICATION_MODAL);
        this.total = Money.scale(total);
        this.symbol = symbol;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);

        GradientPanel header = new GradientPanel(new BorderLayout());
        header.setColors(Theme.PRIMARY, Theme.ACCENT);
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JLabel heading = new JLabel("Amount Due  " + Money.format(symbol, this.total));
        heading.setFont(Theme.bold(18));
        heading.setForeground(Color.WHITE);
        header.add(heading, BorderLayout.WEST);

        RoundedPanel card = new RoundedPanel(new GridLayout(0, 1, 0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        Ui.styleField(cashField);
        cashField.setFont(Theme.bold(20));
        cashField.setHorizontalAlignment(JTextField.RIGHT);
        cashField.getDocument().addDocumentListener(new DefaultDocListener() {
            @Override
            public void changed() {
                recompute();
            }
        });

        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quick.setOpaque(false);
        RoundedButton exact = new RoundedButton("Exact", Theme.withAlpha(Theme.PRIMARY, 30), Theme.PRIMARY);
        exact.addActionListener(e -> cashField.setText(this.total.toPlainString()));
        quick.add(exact);
        for (String note : new String[] {"100", "500", "1000", "5000"}) {
            RoundedButton add = new RoundedButton("+" + note, Theme.withAlpha(Theme.MUTED, 30), Theme.TEXT);
            add.addActionListener(e -> addCash(new BigDecimal(note)));
            quick.add(add);
        }

        changeLabel.setFont(Theme.bold(22));
        changeLabel.setForeground(Theme.MUTED);

        card.add(Ui.formLabel("CASH RECEIVED"));
        card.add(cashField);
        card.add(quick);
        card.add(changeLabel);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.BG);
        wrap.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        wrap.add(card, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setBackground(Theme.BG);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        RoundedButton cancel = new RoundedButton("Cancel", Theme.withAlpha(Theme.MUTED, 40), Theme.TEXT);
        cancel.addActionListener(e -> dispose());
        confirmButton = new RoundedButton("Confirm & Print", Theme.SUCCESS, Color.WHITE);
        confirmButton.addActionListener(e -> confirm());
        footer.add(cancel);
        footer.add(confirmButton);

        root.add(header, BorderLayout.NORTH);
        root.add(wrap, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(new Dimension(440, 360));
        setLocationRelativeTo(owner);

        cashField.setText(this.total.toPlainString());
        recompute();
    }

    private void addCash(BigDecimal note) {
        BigDecimal current = Money.parse(cashField.getText());
        cashField.setText(current.add(note).toPlainString());
    }

    private void recompute() {
        BigDecimal paid = Money.parse(cashField.getText());
        BigDecimal change = paid.subtract(total);
        if (change.signum() < 0) {
            changeLabel.setForeground(Theme.DANGER);
            changeLabel.setText("Short by " + Money.format(symbol, change.negate()));
            confirmButton.setEnabled(false);
        } else {
            changeLabel.setForeground(Theme.SUCCESS);
            changeLabel.setText("Change  " + Money.format(symbol, change));
            confirmButton.setEnabled(true);
        }
    }

    private void confirm() {
        BigDecimal paid = Money.parse(cashField.getText());
        if (paid.compareTo(total) < 0) {
            return;
        }
        amountPaid = Money.scale(paid);
        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }
}
