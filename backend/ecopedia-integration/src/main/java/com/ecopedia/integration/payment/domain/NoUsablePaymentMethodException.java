package com.ecopedia.integration.payment.domain;

/**
 * No hay con qué cobrar: la tarjeta elegida no existe, no es del conductor, está dada de baja o
 * vencida; o no se eligió ninguna y el conductor no tiene ninguna que sirva.
 *
 * <p>Una sola excepción para todos los casos, a propósito: distinguir "no existe" de "es de otro"
 * le confirmaría a quien prueba ids que la tarjeta existe (SEC-06). El motivo va al log.
 */
public class NoUsablePaymentMethodException extends RuntimeException {

    public NoUsablePaymentMethodException(String message) {
        super(message);
    }
}
