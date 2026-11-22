package com.ecopedia.integration.payment.domain;

import java.util.List;

/**
 * El cobro de las deudas del conductor, dentro del componente {@code Pagos} (RF02, RNF06, ECO-62).
 *
 * <p>Decidido por Manuel el 20/11: después del cobro original que falló, tres reintentos cada 20
 * minutos; mientras haya deuda, el conductor no reserva ni habilita; si fallan los tres, la cuenta
 * queda marcada —se anulan sus reservas confirmadas con la seña devuelta— y solo se salda cuando el
 * conductor la paga desde el perfil.
 *
 * <p>Va aparte de {@link PaymentService} porque tiene su propio ciclo: un barrido con reloj y un estado
 * por deuda. Es el mismo componente: comparte la pasarela, las tarjetas y la tabla de deudas.
 */
public interface DebtCollectionService {

    /** Lo que el conductor debe (pendiente o vencido), de lo más viejo a lo más nuevo. */
    List<PendingPayment> listOpenDebts(Long driverId);

    /**
     * El conductor salda una deuda desde el perfil, con la tarjeta que elija. Vale tanto para una
     * pendiente como para una vencida. Una deuda ya saldada se devuelve como está, sin cobrar: así un
     * pedido repetido después de una respuesta perdida no cobra dos veces.
     *
     * @param paymentMethodId la tarjeta elegida, o {@code null} para la más reciente que sirva
     * @param idempotencyKey la misma en cada reintento del mismo pago
     * @throws DebtNotFoundException si la deuda no existe o es de otro
     * @throws NoUsablePaymentMethodException si no hay con qué cobrar
     * @throws DebtPaymentDeclinedException si la pasarela rechazó el cobro
     * @throws DebtPaymentInProgressException si ya hay otro cobro de esa deuda en curso
     * @throws PaymentGatewayUnavailableException si la pasarela no contestó
     */
    PendingPayment payDebt(Long driverId, Long debtId, Long paymentMethodId, String idempotencyKey);

    /**
     * El barrido: reintenta las deudas pendientes cuyo reintento ya tocó y, si alguna queda vencida,
     * pide anular las reservas del conductor.
     *
     * @return cuántas deudas se saldaron
     */
    int retryDueDebts();

    /**
     * Vuelve a pedir la anulación de las reservas de las deudas vencidas que quedaron sin anular
     * porque Reservas no contestó.
     *
     * @return cuántas cuentas quedaron con las reservas anuladas
     */
    int cancelBookingsOfDefaultedDebts();
}
