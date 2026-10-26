package com.ecopedia.integration.payment.domain;

/**
 * Puerto de salida hacia la pasarela de pagos.
 *
 * <p><b>Es el puerto que ARQUITECTURA §2.6 llama {@code PaymentGatewayPort}</b>, y en el patrón
 * Adapter (ECO-56) cumple el rol de <i>Target</i>: es la interfaz que Pagos espera, escrita en el
 * idioma de Ecopedia. El componente de Pagos habla con este contrato y nunca con un cliente HTTP.
 *
 * <p><b>Lo implementa {@code PaymentGatewayRestAdapter}</b>, que traduce cada llamada a la API REST
 * de {@code pasarela-simulada}, el proceso aparte que pide RNF04 (ECO-50). Antes lo implementaba
 * {@code LocalPaymentGateway}, en memoria; el cambio de uno a otro no tocó {@code PaymentServiceImpl}
 * ni la capa de datos, que es exactamente lo que este puerto estaba para garantizar.
 *
 * <p>De este puerto cuelgan más adelante {@code chargeDeposit}, {@code validateCard},
 * {@code chargeConsumption} y {@code refund}. Se agregan cuando haya quién las llame: la pasarela
 * ya expone los endpoints, pero declararlas acá sin consumidor sería llenar la interfaz de métodos
 * que nadie usa.
 */
public interface PaymentGatewayPort {

    /**
     * Cambia el número de una tarjeta por un token que la representa.
     *
     * <p>Es la operación que hace posible RF02: a partir de acá el sistema opera con el token y el
     * número deja de existir para nosotros. La pasarela es además la que valida —número, marca,
     * vencimiento—, porque es la única que puede decir si esa tarjeta es cobrable de verdad.
     *
     * @throws IllegalArgumentException si la pasarela rechaza la tarjeta. Es la misma excepción con
     *     la que el resto del sistema señala "el dato que llegó no sirve", y el manejador de la
     *     capa web la traduce a un 400 con el motivo puesto para que el conductor lo lea.
     */
    TokenizedCard tokenize(CardData card);
}
