package com.smartbillingpro.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Salted PBKDF2 hashing for the optional startup PIN. */
public final class Passwords {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private Passwords() {
    }

    public static String hash(String secret) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] key = derive(secret, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(key);
    }

    public static boolean verify(String secret, String stored) {
        if (stored == null || !stored.startsWith("pbkdf2$")) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        return MessageDigest.isEqual(expected, derive(secret, salt, iterations));
    }

    private static byte[] derive(String secret, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec((secret == null ? "" : secret).toCharArray(), salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        }
    }
}
