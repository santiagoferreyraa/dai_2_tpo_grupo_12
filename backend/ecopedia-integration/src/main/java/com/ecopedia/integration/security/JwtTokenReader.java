package com.ecopedia.integration.security;

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
 * <p><b>Lee pero no emite, y ahí está la diferencia con el {@code JwtTokenProvider} de core.</b>
 * Emitir un token es responsabilidad del componente de Usuarios y de nadie más: si este
 * artefacto pudiera firmar, existiría un segundo lugar del sistema capaz de fabricar
 * credenciales, y el rol que dijera ese token no lo habría autenticado nadie. Por eso esta clase
 * no es una copia recortada de la de core por comodidad; es una responsabilidad más chica.
 *
 * <p><b>Por qué no se comparte la clase entre los dos artefactos.</b> Sería un módulo Maven más y
 * una dependencia entre dos servicios que hoy no se conocen, para reutilizar una llamada a la
 * biblioteca de JWT. Lo que de verdad tienen en común no es el código: es la clave. Esa sí está
 * compartida —{@code ecopedia.jwt.secret}, la misma variable de entorno en los dos— y es lo único
 * que tiene que coincidir para que un token emitido allá valga acá.
 *
 * <p>Es lo que ARQUITECTURA §2.2 describe como autenticación stateless: el token viaja y cada
 * componente lo resuelve en su propia capa de presentación, sin preguntarle a nadie.
 */
@Component
public class JwtTokenReader {

    private final SecretKey key;

    public JwtTokenReader(@Value("${ecopedia.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Devuelve los claims del token, o vacío si la firma no verifica o el token venció.
     *
     * <p>Un {@link Optional} en vez del par validar/leer que tiene core: con dos métodos, quien
     * los usa puede leer sin haber validado y el compilador no dice nada. Acá no hay claims sin
     * validación previa porque no hay otro camino para obtenerlos.
     */
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
