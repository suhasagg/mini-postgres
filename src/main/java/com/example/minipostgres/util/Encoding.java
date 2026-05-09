package com.example.minipostgres.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Encoding {
    private Encoding() {}

    public static String encode(String value) {
        if (value == null) return "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String value) {
        if (value == null || value.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
