package com.ecopedia.async.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Tokens que un atacante le mandaría a la API (QA-07, eje D de los criterios de testing).
 *
 * <p>Los cuatro casos que la cátedra prueba en vivo: vencido, con la firma alterada, con
 * {@code alg=none} y de otro emisor. Más uno válido, que cada test usa como control: si el token
 * bueno tampoco entrara, el rechazo de los malos no probaría nada.
 *
 * <p>El de {@code alg=none} y el alterado se arman a mano, en Base64, y no con jjwt: la biblioteca
 * no deja construir un token roto, que es justo lo que hace falta acá.
 *
 * <p>Hay una copia de esta clase en cada módulo, porque los módulos no comparten código de test.
 */
public final class HostileTokens {

    /**
     * La clave de un emisor que no es Ecopedia: 32 bytes al azar en cada corrida.
     *
     * <p>Al azar y no escrita en el código, porque una cadena con forma de clave en un archivo es
     * justo lo que gitleaks marca, y el docente lo corre en vivo sobre el historial (G6).
     */
    private static final String FOREIGN_SECRET = randomSecret();

    private static final String FOREIGN_ISSUER = "https://emisor-ajeno.example";

    private static final String BEARER = "Bearer ";

    private HostileTokens() {}

    /** Un token correcto, como el que emite el login. */
    public static String valid(String secret, long userId, String role) {
        return BEARER + sign(secret, userId, role, Instant.now(), null);
    }

    /** Bien firmado, pero venció hace una hora. */
    public static String expired(String secret, long userId, String role) {
        return BEARER + sign(secret, userId, role, Instant.now().minus(Duration.ofHours(2)), null);
    }

    /** Firmado por otro emisor, con su propia clave y su propio {@code iss}. */
    public static String fromAnotherIssuer(long userId, String role) {
        return BEARER + sign(FOREIGN_SECRET, userId, role, Instant.now(), FOREIGN_ISSUER);
    }

    /** Sin firma: cabecera {@code alg=none} y la tercera parte vacía. */
    public static String algNone(long userId, String role) {
        long now = Instant.now().getEpochSecond();
        String header = encode("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"%d\",\"email\":\"%s@ecopedia.test\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d}"
                .formatted(userId, role.toLowerCase(), role, now, now + 3600));
        return BEARER + header + "." + payload + ".";
    }

    /**
     * Un token válido al que se le cambió el cuerpo y se le dejó la firma original.
     *
     * @param validBearer el {@code Bearer ...} de partida, bien firmado
     * @param find el texto del cuerpo a reemplazar, p. ej. {@code "role":"CONDUCTOR"}
     * @param replacement el texto que lo reemplaza
     */
    public static String tampered(String validBearer, String find, String replacement) {
        String[] parts = validBearer.substring(BEARER.length()).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        if (!payload.contains(find)) {
            throw new IllegalArgumentException("El cuerpo del token no contiene " + find + ": " + payload);
        }
        return BEARER + parts[0] + "." + encode(payload.replace(find, replacement)) + "." + parts[2];
    }

    private static String sign(String secret, long userId, String role, Instant issuedAt, String issuer) {
        JwtBuilder builder = Jwts.builder()
                .subject(Long.toString(userId))
                .claim("email", role.toLowerCase() + "@ecopedia.test")
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(Duration.ofHours(1))));
        if (issuer != null) {
            builder = builder.issuer(issuer);
        }
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
