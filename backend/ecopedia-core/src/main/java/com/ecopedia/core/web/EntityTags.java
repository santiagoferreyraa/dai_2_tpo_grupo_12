package com.ecopedia.core.web;

import com.ecopedia.core.shared.StaleVersionException;
import com.ecopedia.core.shared.VersionCondition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * El ETag de una estación o un perfil y la lectura del {@code If-Match} (QA-30, RFC 9110 §8.8.3 y
 * §13.1.1).
 *
 * <p><b>El ETag es la versión de la fila entre comillas</b> ({@code "3"}): la que Hibernate sube en
 * cada cambio ({@code @Version}). Sirve para el control de concurrencia de los {@code PUT}, no para
 * cachear: la API no contesta 304 y Spring Security ya manda {@code Cache-Control: no-store}.
 *
 * <p><b>Fuerte, no débil</b> ({@code W/"3"}): {@code If-Match} compara en modo fuerte, así que con
 * una etiqueta débil ningún {@code PUT} pasaría nunca.
 *
 * <p>La versión también viaja en el cuerpo ({@code version}), porque el listado de estaciones no
 * puede llevar una cabecera por estación y {@code /admin} edita desde el listado.
 */
public final class EntityTags {

    /** Lo que dice el contrato OpenAPI de la cabecera. */
    public static final String HEADER_DESCRIPTION =
            "Versión del recurso. Mandarla en If-Match al guardar: si cambió en el medio, 412.";

    private EntityTags() {}

    /** El ETag de una versión. */
    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Aplica una escritura con condición de versión. Si otro guardó entre la comprobación y el
     * commit, Hibernate lo detecta por {@code @Version} y la escritura no pisa nada: con
     * {@code If-Match} es el mismo 412 que una versión vieja; sin la cabecera no había ninguna
     * condición que incumplir, y sale como el 409 de cualquier choque de escrituras.
     */
    public static <T> T write(VersionCondition condition, Supplier<T> write) {
        try {
            return write.get();
        } catch (OptimisticLockingFailureException concurrent) {
            if (!condition.isConditional()) {
                throw concurrent;
            }
            throw new StaleVersionException("Otra escritura guardó en el medio: " + concurrent.getMessage());
        }
    }

    /**
     * Traduce la cabecera {@code If-Match} a la condición de la escritura.
     *
     * <ul>
     *   <li>Sin cabecera, o en blanco: sin condición (decisión del 25/11, ver {@link VersionCondition}).
     *   <li>{@code *}: cualquier versión, como pide la RFC para un recurso que existe.
     *   <li>Una lista de etiquetas: solo las fuertes con un número adentro. Una débil o una que no es
     *       nuestra no coincide con nada, así que una cabecera que no trae ninguna válida da 412.
     * </ul>
     */
    public static VersionCondition ifMatch(String header) {
        if (header == null || header.isBlank()) {
            return VersionCondition.any();
        }
        Set<Long> versions = new HashSet<>();
        for (String raw : header.split(",")) {
            String tag = raw.trim();
            if ("*".equals(tag)) {
                return VersionCondition.any();
            }
            parse(tag, versions);
        }
        return VersionCondition.oneOf(versions);
    }

    private static void parse(String tag, Set<Long> versions) {
        if (tag.length() < 3 || tag.charAt(0) != '"' || tag.charAt(tag.length() - 1) != '"') {
            return;
        }
        String number = withoutEncoding(tag.substring(1, tag.length() - 1));
        if (number.isEmpty() || number.length() > 18 || !number.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return;
        }
        versions.add(Long.parseLong(number));
    }

    /**
     * Caddy, al comprimir una respuesta, le agrega la compresión al ETag ({@code "3"} pasa a
     * {@code "3-gzip"}) y no la saca del {@code If-Match} que vuelve (visto en vivo el 25/11). Sin
     * esto, quien guardara con el ETag tal como le llegó por Caddy recibiría un 412 falso. La versión
     * es la misma: la compresión cambia los bytes, no el recurso.
     */
    private static String withoutEncoding(String opaque) {
        for (String suffix : ENCODING_SUFFIXES) {
            if (opaque.endsWith(suffix)) {
                return opaque.substring(0, opaque.length() - suffix.length());
            }
        }
        return opaque;
    }

    private static final List<String> ENCODING_SUFFIXES = List.of("-gzip", "-zstd", "-br", "-deflate");
}
