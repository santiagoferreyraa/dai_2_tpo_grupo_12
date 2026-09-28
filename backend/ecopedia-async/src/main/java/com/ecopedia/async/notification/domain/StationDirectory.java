package com.ecopedia.async.notification.domain;

import java.util.Optional;

/**
 * Puerto de salida hacia Terminales: de qué estación es un conector y cómo se llama.
 *
 * <p>Existe porque el mensaje de Reservas trae ids y el aviso tiene que decir un nombre. La
 * implementación le pregunta a {@code ecopedia-core} por REST.
 *
 * <p><b>Nunca lanza:</b> si core no contesta o el conector ya no existe, devuelve vacío y el aviso
 * se escribe sin el nombre. Que core esté caído no es motivo para que el conductor se quede sin su
 * aviso, ni para que el mensaje vuelva a la cola a reintentarse diez veces.
 */
public interface StationDirectory {

    Optional<StationInfo> findByConnector(Long connectorId);
}
