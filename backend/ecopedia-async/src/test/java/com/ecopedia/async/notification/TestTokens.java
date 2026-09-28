package com.ecopedia.async.notification;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Tokens como los que emite el login de core, firmados de verdad para que cada prueba recorra el
 * filtro que valida la firma. Ver el {@code TestTokens} de {@code ecopedia-charging}.
 */
final class TestTokens {

    private TestTokens() {}

    static String bearer(String signingSecret, long userId, String role) {
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
