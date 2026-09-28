package com.ecopedia.async.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lee y valida los tokens JWT que emitió Usuarios, que vive en {@code ecopedia-core}.
 *
 * <p>Es la tercera copia de esta clase —las otras dos están en {@code ecopedia-charging} y
 * {@code ecopedia-integration}— y la razón para no compartirla es la misma que explica la de
 * Pagos: sería un módulo Maven más y una dependencia entre servicios que no se conocen, para
 * reutilizar una llamada a la biblioteca de JWT. Lo que tiene que coincidir entre los artefactos
 * es la clave, no el código.
 *
 * <p>Lee pero no emite: fabricar credenciales es de Usuarios y de nadie más.
 */
@Component
public class JwtTokenReader {

    private final SecretKey key;

    public JwtTokenReader(
            @Value("${ecopedia.jwt.secret:EcopediaSecretKeyForJWTAuthentication2026SuperSecureKey!}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Devuelve los claims del token, o vacío si la firma no verifica o el token venció. */
    public Optional<Claims> read(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException rejected) {
            // Firma inválida, token manipulado o vencido: para el caso es lo mismo, no hay sesión.
            return Optional.empty();
        }
    }
}
