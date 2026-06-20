package com.smartbillingpro;

import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.LoginFrame;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Shared startup logic used by every launcher: the full app
 * ({@link SmartBillingApp}), the admin-only app ({@link AdminApp}) and the
 * store-only app ({@link StoreApp}). The only difference between them is the
 * {@link LoginFrame.Mode} they pass in.
 */
final class Bootstrap {

    private Bootstrap() {
    }

    static void start(LoginFrame.Mode mode) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
        Theme.apply();
        SwingUtilities.invokeLater(() -> {
            try {
                AppContext context = new AppContext();
                LoginFrame.launch(context, mode);
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
