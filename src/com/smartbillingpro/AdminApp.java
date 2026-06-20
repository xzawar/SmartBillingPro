package com.smartbillingpro;

import com.smartbillingpro.ui.LoginFrame;

/**
 * Dedicated launcher that ONLY opens the administrator console (creating stores
 * and store-owner accounts). Store owners, managers and cashiers cannot sign in
 * here - they must use {@link StoreApp}.
 *
 * <p>Build this into its own {@code .exe} with {@code Build-Admin-Exe.bat}.</p>
 */
public class AdminApp {
    public static void main(String[] args) {
        Bootstrap.start(LoginFrame.Mode.ADMIN_ONLY);
    }
}
