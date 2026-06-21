package com.smartbillingpro;

import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.LoginFrame;
import com.smartbillingpro.ui.theme.Theme;
import com.smartbillingpro.util.CloudConfig;
import com.smartbillingpro.util.EnvSetup;

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
        // Automatically add the SMARTBILLING_CLOUD_API environment variable to
        // the computer every time the app opens (persists for future processes).
        EnvSetup.ensure();
        // Auto-create the cloud connection setting on first run, so the app
        // connects to the web backend without any manual environment variables.
        CloudConfig.bootstrap();
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
