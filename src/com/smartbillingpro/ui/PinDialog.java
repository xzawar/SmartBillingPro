package com.smartbillingpro.ui;

import com.smartbillingpro.app.AppContext;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Window;

/** Optional startup / lock-screen PIN. There are no user accounts: one PIN protects the till. */
public final class PinDialog {

    private PinDialog() {
    }

    /**
     * Ask for the PIN until it is correct. When {@code allowCancel} is false
     * (startup) closing the dialog exits; returns false in that case.
     */
    public static boolean unlock(Window owner, AppContext ctx, boolean lockScreen) {
        if (!ctx.settings().get().pinEnabled()) {
            return true;
        }
        JDialog d = new JDialog(owner, lockScreen ? "Locked" : "Smart Billing Pro", JDialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(lockScreen ? JDialog.DO_NOTHING_ON_CLOSE : JDialog.DISPOSE_ON_CLOSE);
        final boolean[] ok = {false};
        JPasswordField pin = new JPasswordField(10);
        pin.setHorizontalAlignment(SwingConstants.CENTER);
        pin.setFont(Theme.bold(22));
        JLabel error = Ui.muted(" ");
        error.setForeground(Theme.BAD);
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(Theme.SURFACE);
        p.setBorder(BorderFactory.createEmptyBorder(24, 28, 22, 28));
        JLabel logo = new JLabel(AppIcon.icon(48));
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel title = Ui.heading(ctx.settings().get().storeName());
        title.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel hint = Ui.muted("Enter your PIN to continue");
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(Ui.vbox(6, logo, title, hint), BorderLayout.NORTH);
        p.add(pin, BorderLayout.CENTER);
        javax.swing.JButton go = Ui.primary("Unlock", "lock");
        p.add(Ui.vbox(6, error, go), BorderLayout.SOUTH);
        Runnable check = () -> {
            if (ctx.settings().checkPin(new String(pin.getPassword()))) {
                ok[0] = true;
                d.dispose();
            } else {
                error.setText("Wrong PIN. Please try again.");
                pin.setText("");
                pin.requestFocusInWindow();
            }
        };
        go.addActionListener(e -> check.run());
        pin.addActionListener(e -> check.run());
        d.setContentPane(p);
        d.pack();
        d.setSize(Math.max(340, d.getWidth()), d.getHeight());
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return ok[0];
    }
}
