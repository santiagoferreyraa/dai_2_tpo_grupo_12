package com.ecopedia.core.shared;

import java.util.Set;

/**
 * La condición de una escritura sobre la versión del recurso (QA-30): qué versiones guardadas
 * permiten aplicarla.
 *
 * <p><b>Vive en el dominio y no en la capa web</b> porque la comparación tiene que hacerse sobre la
 * misma entidad que después se guarda, adentro del servicio. Si el controlador leyera la versión
 * antes y el servicio volviera a cargar la entidad, otro podría guardar en el medio y la escritura
 * pasaría igual. La capa web solo traduce la cabecera {@code If-Match} a esta clase.
 *
 * <p>Sin {@code If-Match} la escritura se aplica como antes ({@link #any()}): decidido por Manuel el
 * 25/11, para no romper a los clientes que no la mandan (Postman, Schemathesis, {@code curl}).
 */
public final class VersionCondition {

    private static final VersionCondition ANY = new VersionCondition(null);

    /** Las versiones aceptadas, o {@code null} si cualquiera sirve. */
    private final Set<Long> accepted;

    private VersionCondition(Set<Long> accepted) {
        this.accepted = accepted;
    }

    /** Sin condición: se aplica sea cual sea la versión guardada. */
    public static VersionCondition any() {
        return ANY;
    }

    /** Solo si la versión guardada es una de estas. Con el conjunto vacío no se aplica nunca. */
    public static VersionCondition oneOf(Set<Long> versions) {
        return new VersionCondition(Set.copyOf(versions));
    }

    /** Si hay una condición que comprobar; sin ella, un choque de escrituras no es un 412. */
    public boolean isConditional() {
        return accepted != null;
    }

    /**
     * Rechaza la escritura si la versión guardada no es una de las aceptadas.
     *
     * @throws StaleVersionException si no lo es
     */
    public void check(long currentVersion, String resource) {
        if (accepted != null && !accepted.contains(currentVersion)) {
            throw new StaleVersionException(
                    resource + ": versión guardada " + currentVersion + ", el pedido esperaba " + accepted);
        }
    }
}
