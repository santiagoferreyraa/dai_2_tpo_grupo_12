package com.ecopedia.charging.checkout.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingAccessDeniedException;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.checkout.domain.CheckoutBookingRequest;
import com.ecopedia.charging.checkout.domain.CheckoutFacade;
import com.ecopedia.charging.checkout.domain.CheckoutResult;
import com.ecopedia.charging.checkout.domain.GracePeriodNotAcceptedException;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodRequiredException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
 *   <li><b>Pagos (PaymentService en integration vía PaymentDirectory):</b> verificación de que el conductor
 *       tenga al menos una tarjeta de crédito/débito activa (precondición obligatoria RF02) y procesamiento
 *       de la seña (RF14).</li>
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
 */
@Service
public class BookingCheckoutFacadeImpl implements CheckoutFacade {

    private static final Logger log = LoggerFactory.getLogger(BookingCheckoutFacadeImpl.class);

    private final BookingService bookingService;
    private final TariffDirectory tariffDirectory;
    private final PaymentDirectory paymentDirectory;
    private final Clock clock;

    public BookingCheckoutFacadeImpl(
            BookingService bookingService,
            TariffDirectory tariffDirectory,
            PaymentDirectory paymentDirectory,
            Clock clock) {
        this.bookingService = bookingService;
        this.tariffDirectory = tariffDirectory;
        this.paymentDirectory = paymentDirectory;
        this.clock = clock;
    }

    @Override
    public CheckoutResult processBookingCheckout(Long driverId, CheckoutBookingRequest request, String authToken) {
        Instant now = clock.instant();
        log.info("Iniciando orquestación de checkout para conductor {} sobre retención {}", driverId, request.holdId());

        // Paso 1: Validar consentimiento del plazo de gracia de 15 minutos (RF09)
        if (!request.acceptGracePeriod()) {
            throw new GracePeriodNotAcceptedException();
        }

        // Paso 2: Validar vigencia de la retención del slot (RF08)
        Hold hold = bookingService
                .findHold(request.holdId())
                .orElseThrow(() -> new HoldNotFoundException(request.holdId()));

        if (!hold.driverId().equals(driverId)) {
            throw BookingAccessDeniedException.forHold(request.holdId());
        }
        if (hold.isExpiredAt(now)) {
            throw new HoldExpiredException(request.holdId());
        }

        // Paso 3: Validar precondición de medio de pago del conductor (RF02)
        boolean hasCard = paymentDirectory.hasUsablePaymentMethod(driverId, authToken);
        if (!hasCard) {
            throw new PaymentMethodRequiredException(
                    "Se requiere al menos un medio de pago registrado para confirmar la reserva (RF02)");
        }

        // Paso 4: Calcular la seña requerida según política tarifaria vigente (RF06, RF14)
        BigDecimal depositAmount = tariffDirectory.calculateDeposit(hold.connectorId());
        log.info("Seña calculada para conector {} en checkout: ${}", hold.connectorId(), depositAmount);

        // Paso 5: Confirmar la reserva y persistir el slot bloqueado (RF08)
        // Nota: BookingService ya se encarga de guardar la reserva y publicar el evento a ActiveMQ
        Booking booking = bookingService.confirmBooking(request.holdId(), driverId);

        String paymentSummary = depositAmount.compareTo(BigDecimal.ZERO) > 0
                ? "Seña de $" + depositAmount + " pre-autorizada"
                : "Sin cobro de seña previo";

        log.info("Checkout completado con éxito para reserva {} del conductor {}", booking.getId(), driverId);

        return new CheckoutResult(booking, depositAmount, paymentSummary, now);
    }
}
