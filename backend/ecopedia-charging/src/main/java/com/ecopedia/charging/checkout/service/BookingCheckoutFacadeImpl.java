package com.ecopedia.charging.checkout.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingDeposit;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.checkout.domain.CheckoutBookingRequest;
import com.ecopedia.charging.checkout.domain.CheckoutFacade;
import com.ecopedia.charging.checkout.domain.CheckoutResult;
import com.ecopedia.charging.checkout.domain.DepositCharge;
import com.ecopedia.charging.checkout.domain.DepositGateway;
import com.ecopedia.charging.checkout.domain.GracePeriodNotAcceptedException;
import com.ecopedia.charging.checkout.domain.IdempotencyKeyRequiredException;
import com.ecopedia.charging.checkout.domain.IdempotencyKeyReusedException;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodRequiredException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementación del Patrón de Diseño GoF <b>Facade (Fachada)</b> para el proceso de checkout (ECO-35).
 *
 * <p><b>1. Problema de negocio y arquitectura que resuelve:</b>
 * El alta de una reserva o carga no es una operación atómica ni simple. Toca cuatro subsistemas
 * independientes con responsabilidades desacopladas:
 * <ul>
 *   <li><b>Reservas (BookingService):</b> estado conversacional de slots, validación de cruces concurrentes
 *       y persistencia transaccional del bloqueo temporal.</li>
 *   <li><b>Tarificación (PricingService en core vía TariffDirectory):</b> cálculo del importe de la seña
 *       requerida según el esquema de tarifas fijado por el CPO (RF06).</li>
 *   <li><b>Pagos (PaymentService en integration vía PaymentDirectory y DepositGateway):</b> verificación de
 *       que el conductor tenga al menos una tarjeta (precondición RF02) y cobro de la seña (RF14).</li>
 *   <li><b>Mensajería (ActiveMQ Artemis vía BookingEventPublisher):</b> publicación de eventos desacoplados
 *       para que el worker de notificaciones registre el historial y despache el comprobante por email (RF17).</li>
 * </ul>
 *
 * <p><b>2. Por qué el Patrón Facade:</b>
 * El patrón Facade expone <b>una única operación de alto nivel</b> al cliente de la API ({@link #processBookingCheckout})
 * y esconde toda la complejidad de coordinación y secuencia entre los subsistemas. De esta forma:
 * <ul>
 *   <li>Mantiene la capa de presentación (controlador REST) completamente delgada (thin controller).</li>
 *   <li>Evita exponer al frontend la topología interna de múltiples microservicios o endpoints fragmentados.</li>
 *   <li>Centraliza las validaciones de negocio previas (consentimiento de gracia RF09, vigencia del hold RF08,
 *       tarjetas cobrables RF02 y cálculo de seña RF06) antes de comprometer los recursos del sistema.</li>
 * </ul>
 *
 * <p><b>3. Alternativas descartadas y justificación (Requisito de la consigna):</b>
 * <ul>
 *   <li><b>Alternativa 1 — Orquestación en el Frontend:</b> El navegador invoca sucesivamente `/holds`,
 *       `/pricing/estimate`, `/payment-methods` y `/bookings`. Descartada por alto riesgo de inconsistencia ante fallos
 *       de red en conexiones móviles de los conductores (chatter) y falta de atomicidad.</li>
 *   <li><b>Alternativa 2 — Orquestación en el Controlador Web:</b> El {@code CheckoutController} inyecta los 4 servicios
 *       y ejecuta los pasos antes de devolver HTTP. Descartada porque viola la arquitectura en capas requerida,
 *       introduciendo lógica de orquestación de negocio en la capa de transporte/presentación.</li>
 *   <li><b>Alternativa 3 — Acoplamiento en BookingServiceImpl:</b> Inyectar pagos y tarificación dentro de Reservas.
 *       Descartada porque viola el Principio de Responsabilidad Única (SRP), transformando a Reservas en un componente
 *       acoplado a pasarelas de pago externas y fórmulas tarifarias.</li>
 * </ul>
 *
 * <p><b>4. Cobrar antes de confirmar, y devolver si la confirmación falla (ECO-53).</b>
 * El orden es retener → cobrar → confirmar → avisar. Cobrar primero es lo que hace que una reserva
 * confirmada siempre tenga su seña: al revés, un cobro rechazado obligaría a des-confirmar una reserva
 * que ya se avisó. El precio es una <b>compensación</b>: si el cobro salió y la confirmación no —la
 * retención venció durante el cobro, o alguien ganó el turno—, la seña se devuelve en el acto. No hay
 * una transacción que abarque a Pagos: es otro proceso, y la consistencia entre los dos la da la
 * compensación, no un rollback.
 *
 * <p><b>5. Idempotente por la {@code Idempotency-Key} (QA-29).</b> El mismo pedido repetido devuelve la
 * reserva que ya confirmó, sin cobrar ni confirmar de nuevo; y la clave del cobro sale de esa misma clave,
 * así que tampoco Pagos cobra dos veces aunque el reintento llegue a medio camino.
 */
@Service
public class BookingCheckoutFacadeImpl implements CheckoutFacade {

    private static final Logger log = LoggerFactory.getLogger(BookingCheckoutFacadeImpl.class);

    /** La forma de una clave aceptable: la que genera el front (un UUID) entra holgada. */
    private static final Pattern CHECKOUT_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final BookingService bookingService;
    private final TariffDirectory tariffDirectory;
    private final PaymentDirectory paymentDirectory;
    private final DepositGateway depositGateway;
    private final Clock clock;

    public BookingCheckoutFacadeImpl(
            BookingService bookingService,
            TariffDirectory tariffDirectory,
            PaymentDirectory paymentDirectory,
            DepositGateway depositGateway,
            Clock clock) {
        this.bookingService = bookingService;
        this.tariffDirectory = tariffDirectory;
        this.paymentDirectory = paymentDirectory;
        this.depositGateway = depositGateway;
        this.clock = clock;
    }

    @Override
    public CheckoutResult processBookingCheckout(
            Long driverId, CheckoutBookingRequest request, String checkoutKey, String authToken) {
        Instant now = clock.instant();
        log.info("Iniciando orquestación de checkout para conductor {} sobre retención {}", driverId, request.holdId());

        // Paso 0: ¿es un pedido que ya se atendió? (QA-29) Antes que todo: la retención ya no existe.
        if (checkoutKey == null || !CHECKOUT_KEY.matcher(checkoutKey).matches()) {
            throw new IdempotencyKeyRequiredException();
        }
        Optional<Booking> alreadyDone = bookingService.findByCheckoutKey(driverId, checkoutKey);
        if (alreadyDone.isPresent()) {
            return repeated(alreadyDone.get(), request, checkoutKey);
        }

        // Paso 1: Validar consentimiento del plazo de gracia de 15 minutos (RF09)
        if (!request.acceptGracePeriod()) {
            throw new GracePeriodNotAcceptedException();
        }

        // Paso 2: Validar vigencia de la retención del slot (RF08)
        Hold hold = bookingService
                .findHold(request.holdId())
                .orElseThrow(() -> new HoldNotFoundException(request.holdId()));

        if (!hold.driverId().equals(driverId)) {
            // La retención de otro se contesta como inexistente (SEC-06); el motivo, al log.
            log.warn(
                    "Acceso denegado: el conductor {} quiso confirmar la retención {}, que es de otro",
                    driverId,
                    request.holdId());
            throw new HoldNotFoundException(request.holdId());
        }
        if (hold.isExpiredAt(now)) {
            throw new HoldExpiredException(request.holdId());
        }

        // Paso 3: Validar precondición de medio de pago del conductor (RF02), también sin seña
        boolean hasCard = paymentDirectory.hasUsablePaymentMethod(driverId, authToken);
        if (!hasCard) {
            throw new PaymentMethodRequiredException(
                    "Se requiere al menos un medio de pago registrado para confirmar la reserva (RF02)");
        }

        // Paso 4: Calcular la seña requerida según política tarifaria vigente (RF06, RF14)
        BigDecimal depositAmount = tariffDirectory.calculateDeposit(hold.connectorId());
        log.info("Seña calculada para conector {} en checkout: ${}", hold.connectorId(), depositAmount);

        // Paso 5: Cobrarla (RF14). Sin seña no hay nada que cobrar ni que devolver.
        BookingDeposit deposit = BookingDeposit.none();
        if (depositAmount.signum() > 0) {
            DepositCharge charge = depositGateway.charge(
                    driverId,
                    request.paymentMethodId(),
                    depositAmount,
                    "hold-" + hold.id(),
                    "deposit-" + driverId + "-" + checkoutKey);
            deposit = new BookingDeposit(depositAmount, charge.chargeId(), charge.cardLastFour());
        }

        // Paso 6: Confirmar la reserva (RF08). BookingService guarda y publica el aviso.
        Booking booking;
        try {
            booking = bookingService.confirmBooking(hold.id(), driverId, deposit, checkoutKey);
        } catch (RuntimeException failure) {
            /*
             * Un pedido gemelo con la misma clave pudo ganar la confirmación mientras este cobraba: los dos
             * cobraron el MISMO cobro (la clave del cobro es la misma) y ese cobro ya es la seña de la
             * reserva del otro. Devolverlo acá le sacaría la seña a una reserva válida.
             */
            Optional<Booking> twin = bookingService.findByCheckoutKey(driverId, checkoutKey);
            if (twin.isPresent()) {
                return repeated(twin.get(), request, checkoutKey);
            }
            compensate(deposit, driverId, hold, failure);
            throw failure;
        }

        log.info("Checkout completado con éxito para reserva {} del conductor {}", booking.getId(), driverId);
        return CheckoutResult.of(booking);
    }

    /** La respuesta a un pedido que ya se atendió, si de verdad es el mismo pedido. */
    private static CheckoutResult repeated(Booking booking, CheckoutBookingRequest request, String checkoutKey) {
        if (!booking.confirmedHold(request.holdId())) {
            log.warn(
                    "La clave {} ya confirmó la reserva {}, de otra retención: se rechaza",
                    checkoutKey,
                    booking.getId());
            throw new IdempotencyKeyReusedException();
        }
        log.info("Checkout repetido con la clave {}: se devuelve la reserva {}", checkoutKey, booking.getId());
        return CheckoutResult.of(booking);
    }

    /**
     * Devuelve la seña de un cobro que quedó sin reserva.
     *
     * <p>Si la devolución tampoco sale —Pagos acaba de contestar el cobro, así que es improbable—, la
     * plata del conductor quedó cobrada sin reserva y nada la va a devolver sola: ERROR con todo lo
     * necesario para hacerlo a mano. Una tabla de compensaciones pendientes es la versión robusta, y
     * está anotada como deuda.
     */
    private void compensate(BookingDeposit deposit, Long driverId, Hold hold, RuntimeException failure) {
        if (!deposit.isCharged()) {
            return;
        }
        log.warn(
                "La retención {} no se pudo confirmar después de cobrar la seña ({}): se devuelve el cobro {}",
                hold.id(),
                failure.getClass().getSimpleName(),
                deposit.chargeId());
        try {
            depositGateway.refund(deposit.chargeId());
        } catch (RuntimeException refundFailed) {
            log.error(
                    "SEÑA COBRADA SIN RESERVA: no se pudo devolver el cobro {} de ${} del conductor {} (retención {}). Hay que devolverlo a mano.",
                    deposit.chargeId(),
                    deposit.amount(),
                    driverId,
                    hold.id(),
                    refundFailed);
        }
    }
}
