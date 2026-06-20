package com.smartbillingpro;

import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.LoginFrame;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Application entry point for Smart Billing Pro.
 *
 * <p>Starts the Swing UI on the event dispatch thread, applies the theme and
 * opens the login window. A friendly dialog is shown if startup fails (most
 * commonly because the SQLite JDBC driver is missing from the lib folder).
 */
public class SmartBillingApp {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
        Theme.apply();
        SwingUtilities.invokeLater(() -> {
            try {
                AppContext context = new AppContext();
                new LoginFrame(context).setVisible(true);
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(null,
                        "Smart Billing Pro could not start:\n\n" + t.getMessage()
                                + "\n\nIf this mentions a driver or JDBC, place the SQLite driver\n"
                                + "(sqlite-jdbc-x.x.x.jar) inside the 'lib' folder and try again.",
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
