package com.smartbillingpro;

import com.smartbillingpro.ui.LoginFrame;

/**
 * Dedicated launcher that ONLY opens store accounts (owner / manager / cashier)
 * in the point-of-sale workspace. The administrator cannot sign in here - they
 * must use {@link AdminApp}.
 *
 * <p>Build this into its own {@code .exe} with {@code Build-Store-Exe.bat}.</p>
 */
public class StoreApp {
    public static void main(String[] args) {
        Bootstrap.start(LoginFrame.Mode.STORE_ONLY);
    }
}
