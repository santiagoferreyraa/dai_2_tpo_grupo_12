package com.ecopedia.integration.payment.domain;

/**
 * El alta de una tarjeta no se hizo por algo que el conductor puede corregir: un número mal copiado,
 * una tarjeta vencida, una que ya tiene registrada, el tope de tarjetas.
 *
 * <p>Lleva un {@code code} estable porque el front elige sus propias palabras (directiva 6): el
 * mensaje queda para el log y para el {@code detail}, que la pantalla no muestra. Extiende
 * {@link IllegalArgumentException} para que todo lo que ya trataba estos rechazos como "pedido
 * inválido" —el circuit breaker, que no los cuenta como falla de la pasarela— siga igual.
 */
public class CardRejectedException extends IllegalArgumentException {

    /** El número no es válido: incompleto, con letras o con el dígito verificador mal. */
    public static final String CARD_NUMBER_INVALID = "CARD_NUMBER_INVALID";

    /** El mes de vencimiento no existe. */
    public static final String CARD_EXPIRY_INVALID = "CARD_EXPIRY_INVALID";

    public static final String CARD_EXPIRED = "CARD_EXPIRED";

    /** Falta el nombre del titular. */
    public static final String CARDHOLDER_NAME_MISSING = "CARDHOLDER_NAME_MISSING";

    /** La pasarela simulada solo acepta su catálogo de tarjetas de prueba. */
    public static final String TEST_CARD_REQUIRED = "TEST_CARD_REQUIRED";

    /** La pasarela la rechazó por un motivo que no tiene traducción propia. */
    public static final String CARD_REJECTED = "CARD_REJECTED";

    public static final String CARD_ALREADY_REGISTERED = "CARD_ALREADY_REGISTERED";

    /** El conductor ya tiene el máximo de tarjetas activas. */
    public static final String CARD_LIMIT_REACHED = "CARD_LIMIT_REACHED";

    private final String code;

    public CardRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
