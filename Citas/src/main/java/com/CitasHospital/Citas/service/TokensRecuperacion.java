package com.CitasHospital.Citas.service;

import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

public final class TokensRecuperacion {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TokensRecuperacion() { }
    public static String nuevo() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.");
        }
    }
    public static boolean formatoValido(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }
}
