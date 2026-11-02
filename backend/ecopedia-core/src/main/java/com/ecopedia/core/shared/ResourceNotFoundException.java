package com.ecopedia.core.shared;

/**
 * Lo que se buscó por id no existe: estación, conector o usuario. La API lo contesta como el 404
 * de cualquier ruta inexistente; el mensaje, con el id, queda para el log.
 *
 * <p>Extiende {@link IllegalArgumentException} para no cambiarles el comportamiento a quienes ya
 * la atrapaban así (por ejemplo, {@code TerminalAccess} y Tarificación, que piden una estación a
 * Terminales). Los manejadores de errores la atienden antes por ser más específica.
 */
public class ResourceNotFoundException extends IllegalArgumentException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
