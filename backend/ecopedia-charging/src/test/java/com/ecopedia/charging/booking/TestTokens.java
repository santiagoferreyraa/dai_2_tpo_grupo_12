package com.ecopedia.charging.booking;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Tokens como los que emite el login de core.
 *
 * <p><b>Se firman de verdad, en vez de simular un usuario autenticado</b>: así cada prueba
 * recorre el filtro que valida la firma, que es la pieza que une los dos procesos y la que puede
 * romperse en silencio si alguien cambia el secreto de un lado solo.
 */
public final class TestTokens {

    private TestTokens() {}

    public static String bearer(String signingSecret, long userId, String role) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(Long.toString(userId))
                .claim("email", role.toLowerCase() + "@ecopedia.test")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }
}
