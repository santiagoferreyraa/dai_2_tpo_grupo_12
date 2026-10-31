package com.ecopedia.integration.payment.domain;

import java.util.List;

/**
 * Interfaz pública del componente {@code Pagos} (stateless).
 *
 * <p>Cubre las tres operaciones de RF02 que pidió ECO-26 —registrar, consultar y eliminar los medios
 * de pago del conductor— y, desde ECO-53, las dos de la seña: cobrarla al confirmar la reserva y
 * devolverla. Las demás del contrato de ARQUITECTURA §2.6 —{@code validateCard},
 * {@code chargeConsumption}, {@code getOutstandingDebt}— se agregan cuando exista quien las llame
 * (SesionesDeCarga). Declararlas antes sería llenar la interfaz de métodos que ninguna prueba cubre.
 *
 * <p><b>Sobre la precondición de RF02.</b> "Tener al menos una tarjeta registrada es precondición de
 * toda transacción" es una regla que se aplica al reservar y al iniciar una carga, no al registrar
 * una tarjeta. Este componente la hace verificable —{@link #listCards} y
 * {@link PaymentMethod#isUsable}— y quien la exige es Reservas, en ECO-32. Meter la exigencia acá
 * sería ponerla en el único lugar del sistema donde no hay nada que exigir.
 */
public interface PaymentService {

    /**
     * Registra una tarjeta para un conductor y devuelve lo que quedó guardado.
     *
     * <p>El número va a la pasarela y no vuelve: lo que se persiste es marca, últimos cuatro y
     * token.
     */
    PaymentMethod registerCard(Long driverId, CardData data);

    /** Las tarjetas vigentes del conductor. */
    List<PaymentMethod> listCards(Long driverId);

    /**
     * Da de baja una tarjeta del conductor.
     *
     * <p><b>Recibe el dueño además del id de la tarjeta, y el contrato del documento no.</b> Ahí la
     * operación es {@code removeCard(cardId)} con el rol anotado como "DRIVER dueño"; acá el dueño
     * viaja como parámetro para que la comprobación viva en el negocio y no en el controlador. La
     * diferencia importa: {@code @PreAuthorize} sabe decir "es un conductor", pero no "es el
     * conductor de ESTA tarjeta", porque para saberlo hay que ir a buscarla. Dejar esa verificación
     * en la capa web sería poner una regla de dominio donde no se la puede probar sin levantar HTTP.
     */
    void removeCard(Long driverId, Long cardId);

    /**
     * Cobra la seña de una reserva (RF14). Cobro efectivo, no una retención: la plata sale de la
     * tarjeta en ese momento.
     *
     * <p><b>Idempotente por la clave del pedido.</b> El mismo pedido repetido devuelve el mismo
     * cobro —aprobado o rechazado— sin volver a la pasarela. Un rechazo vuelve como un cobro con
     * estado {@link DepositChargeStatus#DECLINED}, no como excepción: es una respuesta del negocio.
     *
     * @throws NoUsablePaymentMethodException si no hay tarjeta con la que cobrar
     * @throws IdempotencyKeyReusedException si la clave ya se usó para otro cobro
     * @throws PaymentGatewayUnavailableException si la pasarela no contestó: no se sabe si cobró, y
     *     por eso no queda nada guardado y el reintento va con la misma clave
     */
    DepositCharge chargeDeposit(DepositChargeRequest request);

    /**
     * Devuelve entera la seña de un cobro aprobado. Idempotente: devolver lo ya devuelto contesta lo
     * mismo sin volver a la pasarela.
     *
     * @throws DepositChargeNotFoundException si el cobro no existe
     * @throws DepositNotRefundableException si el cobro fue rechazado
     * @throws PaymentGatewayUnavailableException si la pasarela no contestó; quien llama reintenta
     */
    DepositCharge refundDeposit(Long chargeId);
}
