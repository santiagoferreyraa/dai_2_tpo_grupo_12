package com.ecopedia.integration.payment.domain;

import java.math.BigDecimal;

/**
 * Puerto de salida hacia la pasarela de pagos.
 *
 * <p><b>Es el puerto que ARQUITECTURA §2.6 llama {@code PaymentGatewayPort}</b>, y es el Adapter:
 * el componente de Pagos habla con este contrato y nunca con un cliente HTTP. Lo implementa
 * {@code PaymentGatewayRestClient} contra la pasarela simulada, que corre como proceso aparte
 * (RNF04). Cuando se pasó de la implementación local que había antes a la remota, no cambió una
 * línea de {@code PaymentServiceImpl} ni de la capa de datos: es la prueba de que el patrón sirve.
 *
 * <p><b>Dos formas de fallar, y no son lo mismo.</b> Una pasarela que contesta "no" —tarjeta mal
 * copiada, cobro rechazado— es una respuesta del negocio. Una que no contesta —caída, lenta, mal
 * configurada— es {@link PaymentGatewayUnavailableException}: quien llama decide si reintenta, y
 * al conductor se le dice que pruebe en un rato.
 *
 * <p>{@code charge}, {@code refund} y {@code validate} existen antes que quien las use: las llaman
 * el checkout (la seña) y SesionesDeCarga (validar y cobrar el consumo), que se escriben contra
 * este contrato.
 */
public interface PaymentGatewayPort {

    /**
     * Cambia el número de una tarjeta por un token que la representa.
     *
     * <p>Es la operación que hace posible RF02: a partir de acá el sistema opera con el token y el
     * número deja de existir para nosotros. La pasarela es además la que valida —número, marca,
     * vencimiento—, porque es la única que puede decir si esa tarjeta es cobrable de verdad.
     *
     * @throws IllegalArgumentException si la pasarela rechaza la tarjeta, con el motivo escrito para
     *     el conductor. Es la misma excepción con la que el resto del sistema señala "el dato que
     *     llegó no sirve", y el manejador de la capa web la traduce a un 400.
     * @throws PaymentGatewayUnavailableException si la pasarela no contestó
     */
    TokenizedCard tokenize(CardData card);

    /**
     * Cobra un importe a la tarjeta del token.
     *
     * <p>Un rechazo no es una excepción: vuelve como un {@link GatewayCharge} no aprobado, con el
     * motivo. La clave de idempotencia la elige quien cobra y tiene que ser la misma en cada
     * reintento del mismo cobro ("seña de la reserva 12"): así un reintento nunca cobra dos veces.
     *
     * @throws PaymentGatewayUnavailableException si la pasarela no contestó. Ojo: no contestar no
     *     quiere decir que no cobró. Por eso el reintento va con la misma clave.
     */
    GatewayCharge charge(
            String gatewayToken, BigDecimal amount, String description, String reference, String idempotencyKey);

    /**
     * Devuelve plata de un cobro aprobado. Sin importe ({@code null}), todo lo que queda.
     *
     * @throws IllegalArgumentException si el cobro no existe, fue rechazado o ya se devolvió entero
     * @throws PaymentGatewayUnavailableException si la pasarela no contestó
     */
    GatewayRefund refund(String paymentId, BigDecimal amount, String idempotencyKey);

    /**
     * Pregunta si a la tarjeta se le puede cobrar, sin mover plata (RF11).
     *
     * @throws PaymentGatewayUnavailableException si la pasarela no contestó
     */
    CardValidation validate(String gatewayToken);
}
