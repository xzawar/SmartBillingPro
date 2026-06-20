package com.smartbillingpro;

import com.smartbillingpro.ui.LoginFrame;

/**
 * Application entry point for Smart Billing Pro (full app: accepts every account
 * type at the login screen).
 *
 * <p>There are also two dedicated launchers that share the same code base:
 * {@link AdminApp} opens only the administrator console, and {@link StoreApp}
 * opens only store accounts. All three route through {@link Bootstrap}.</p>
 */
public class SmartBillingApp {

    public static void main(String[] args) {
        Bootstrap.start(LoginFrame.Mode.ALL);
    }
}
