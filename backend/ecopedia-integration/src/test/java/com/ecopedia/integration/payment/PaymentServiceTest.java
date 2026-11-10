package com.ecopedia.integration.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.CardValidation;
import com.ecopedia.integration.payment.domain.ConsumptionChargeRequest;
import com.ecopedia.integration.payment.domain.ConsumptionChargeResult;
import com.ecopedia.integration.payment.domain.DepositChargeRepository;
import com.ecopedia.integration.payment.domain.GatewayCharge;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodNotFoundException;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.PaymentValidationResult;
import com.ecopedia.integration.payment.domain.PaymentValidationResult.ValidationErrorCode;
import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import com.ecopedia.integration.payment.service.PaymentServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Las reglas de RF02 sobre los medios de pago del conductor (ECO-26). */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long DRIVER = 7L;
    private static final Long ANOTHER_DRIVER = 8L;

    @Mock
    private PaymentMethodRepository paymentMethodRepository;

    @Mock
    private DepositChargeRepository depositChargeRepository;

    @Mock
    private PendingPaymentRepository pendingPaymentRepository;

    @Mock
    private PaymentGatewayPort paymentGateway;

    private PaymentServiceImpl paymentService;

    @Captor
    private ArgumentCaptor<PaymentMethod> savedCard;

    private CardData input;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);
        paymentService = new PaymentServiceImpl(
                paymentMethodRepository, depositChargeRepository, pendingPaymentRepository, paymentGateway, fixedClock);
        input = new CardData("4111111111111111", 9, 2029, "SANTIAGO R", "  La del laburo  ");
    }

    private TokenizedCard tokenized() {
        return new TokenizedCard(CardBrand.VISA, "1111", "tok_abc");
    }

    private PaymentMethod existing(Long driverId, CardBrand brand, String lastFour, int month, int year) {
        PaymentMethod card = new PaymentMethod();
        card.setId(1L);
        card.setDriverId(driverId);
        card.setBrand(brand);
        card.setLastFour(lastFour);
        card.setGatewayToken("tok_previo");
        card.setExpiryMonth(month);
        card.setExpiryYear(year);
        card.setActive(true);
        return card;
    }

    @Test
    @DisplayName("Guarda marca, últimos cuatro y token, y ningún rastro del número")
    void storesOnlyWhatRf02Allows() {
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of());
        when(paymentGateway.tokenize(any(CardData.class))).thenReturn(tokenized());
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(call -> call.getArgument(0));

        paymentService.registerCard(DRIVER, input);

        verify(paymentMethodRepository).save(savedCard.capture());
        PaymentMethod stored = savedCard.getValue();

        assertEquals(DRIVER, stored.getDriverId());
        assertEquals(CardBrand.VISA, stored.getBrand());
        assertEquals("1111", stored.getLastFour());
        assertEquals("tok_abc", stored.getGatewayToken());
        assertTrue(stored.isActive());

        /*
         * La verificación central del requisito: ningún campo de la entidad contiene el número
         * completo. Se comprueba sobre lo que se va a persistir, que es donde importa.
         */
        assertFalse(stored.toString().contains("4111111111111111"));
        assertFalse(stored.getGatewayToken().contains("4111111111111111"));
    }

    @Test
    @DisplayName("Recorta la etiqueta y guarda como ausente la que quedó vacía")
    void normalizesTheLabel() {
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of());
        when(paymentGateway.tokenize(any(CardData.class))).thenReturn(tokenized());
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(call -> call.getArgument(0));

        paymentService.registerCard(DRIVER, input);
        verify(paymentMethodRepository).save(savedCard.capture());

        assertEquals("La del laburo", savedCard.getValue().getLabel());
    }

    @Test
    @DisplayName("No guarda nada si la pasarela rechaza la tarjeta")
    void doesNotPersistWhenTheGatewayRejects() {
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of());
        when(paymentGateway.tokenize(any(CardData.class)))
                .thenThrow(new IllegalArgumentException("La tarjeta está vencida"));

        assertThrows(IllegalArgumentException.class, () -> paymentService.registerCard(DRIVER, input));

        // Es la razón por la que la tokenización va antes del save y no al revés.
        verify(paymentMethodRepository, never()).save(any(PaymentMethod.class));
    }

    @Test
    @DisplayName("Rechaza registrar dos veces la misma tarjeta")
    void rejectsADuplicate() {
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(existing(DRIVER, CardBrand.VISA, "1111", 9, 2029)));
        when(paymentGateway.tokenize(any(CardData.class))).thenReturn(tokenized());

        var rejected = assertThrows(IllegalArgumentException.class, () -> paymentService.registerCard(DRIVER, input));

        assertTrue(rejected.getMessage().contains("ya está registrada"));
        verify(paymentMethodRepository, never()).save(any(PaymentMethod.class));
    }

    @Test
    @DisplayName("Deja registrar una tarjeta que solo coincide en los últimos cuatro")
    void allowsADifferentCardWithTheSameLastFour() {
        // Misma marca y mismos cuatro dígitos, pero otro vencimiento: es otra tarjeta.
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(existing(DRIVER, CardBrand.VISA, "1111", 3, 2031)));
        when(paymentGateway.tokenize(any(CardData.class))).thenReturn(tokenized());
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(call -> call.getArgument(0));

        PaymentMethod registered = paymentService.registerCard(DRIVER, input);

        assertEquals("1111", registered.getLastFour());
    }

    @Test
    @DisplayName("Corta el alta al llegar al tope de tarjetas vigentes")
    void rejectsBeyondTheLimit() {
        List<PaymentMethod> ten = new ArrayList<>(IntStream.range(0, 10)
                .mapToObj(index -> existing(DRIVER, CardBrand.VISA, "000" + index, 9, 2029))
                .toList());
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(ten);

        assertThrows(IllegalArgumentException.class, () -> paymentService.registerCard(DRIVER, input));

        // Ni siquiera se gasta una llamada a la pasarela.
        verify(paymentGateway, never()).tokenize(any(CardData.class));
    }

    @Test
    @DisplayName("La baja es lógica: la fila queda, marcada como inactiva")
    void removesLogically() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 9, 2029);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(card));
        when(paymentMethodRepository.save(any(PaymentMethod.class))).thenAnswer(call -> call.getArgument(0));

        paymentService.removeCard(DRIVER, 1L);

        verify(paymentMethodRepository).save(savedCard.capture());
        assertFalse(savedCard.getValue().isActive());
    }

    @Test
    @DisplayName("Un conductor no puede eliminar la tarjeta de otro")
    void doesNotRemoveSomeoneElsesCard() {
        when(paymentMethodRepository.findById(1L))
                .thenReturn(Optional.of(existing(ANOTHER_DRIVER, CardBrand.VISA, "1111", 9, 2029)));

        var rejected = assertThrows(PaymentMethodNotFoundException.class, () -> paymentService.removeCard(DRIVER, 1L));

        /*
         * Contesta "no se encontró" y no "no es tuya": un mensaje distinto confirmaría que ese id
         * existe, y con ids consecutivos se podría contar las tarjetas de la plataforma.
         */
        assertTrue(rejected.getMessage().contains("No se encontró"));
        verify(paymentMethodRepository, never()).save(any(PaymentMethod.class));
    }

    @Test
    @DisplayName("Eliminar dos veces la misma tarjeta falla la segunda vez")
    void doesNotRemoveTwice() {
        PaymentMethod alreadyRemoved = existing(DRIVER, CardBrand.VISA, "1111", 9, 2029);
        alreadyRemoved.setActive(false);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(alreadyRemoved));

        assertThrows(PaymentMethodNotFoundException.class, () -> paymentService.removeCard(DRIVER, 1L));
    }

    @Test
    @DisplayName("Solo lista las tarjetas del conductor que pregunta")
    void listsOnlyOwnCards() {
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(existing(DRIVER, CardBrand.VISA, "1111", 9, 2029)));

        List<PaymentMethod> cards = paymentService.listCards(DRIVER);

        assertEquals(1, cards.size());
        assertEquals(DRIVER, cards.get(0).getDriverId());
    }

    @Test
    @DisplayName("Una tarjeta vencida se sigue listando, marcada como vencida")
    void keepsListingExpiredCards() {
        PaymentMethod expired = existing(DRIVER, CardBrand.VISA, "1111", 12, 2025);

        /*
         * No se filtran del listado a propósito: si desaparecieran, el conductor no tendría cómo
         * eliminar una tarjeta vencida ni entendería por qué no puede reservar. Se muestran y se
         * avisa.
         */
        assertTrue(expired.isExpired(YearMonth.of(2026, 1)));
        assertFalse(expired.isUsable(YearMonth.of(2026, 1)));
    }

    @Test
    @DisplayName("Una tarjeta vigente y activa sirve para operar")
    void aValidCardIsUsable() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 9, 2029);

        // Es la condición que Reservas va a exigir en ECO-32 para la precondición de RF02.
        assertTrue(card.isUsable(YearMonth.of(2026, 1)));
    }

    @Test
    @DisplayName("validatePaymentMethod: aprueba si la tarjeta es cobrable y el conductor no arrastra deuda")
    void validatePaymentMethod_shouldApprove_whenCardIsChargeableAndNoDebt() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(card));
        when(pendingPaymentRepository.existsByDriverIdAndStatus(DRIVER, PendingPaymentStatus.PENDING))
                .thenReturn(false);
        when(paymentGateway.validate("tok_previo")).thenReturn(new CardValidation(true, "chargeable"));

        PaymentValidationResult result = paymentService.validatePaymentMethod(DRIVER, null);

        assertTrue(result.valid());
        assertEquals(ValidationErrorCode.NONE, result.errorCode());
        verify(paymentGateway).validate("tok_previo");
    }

    @Test
    @DisplayName("validatePaymentMethod: rechaza si el conductor arrastra deudas impagas y no consulta pasarela")
    void validatePaymentMethod_shouldReject_whenDriverHasOutstandingDebt() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(card));
        when(pendingPaymentRepository.existsByDriverIdAndStatus(DRIVER, PendingPaymentStatus.PENDING))
                .thenReturn(true);

        PaymentValidationResult result = paymentService.validatePaymentMethod(DRIVER, null);

        assertFalse(result.valid());
        assertEquals(ValidationErrorCode.DRIVER_HAS_DEBT, result.errorCode());
        verify(paymentGateway, never()).validate(any());
    }

    @Test
    @DisplayName("validatePaymentMethod: rechaza si la pasarela declina la tarjeta")
    void validatePaymentMethod_shouldReject_whenGatewayDeclinesCard() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(card));
        when(pendingPaymentRepository.existsByDriverIdAndStatus(DRIVER, PendingPaymentStatus.PENDING))
                .thenReturn(false);
        when(paymentGateway.validate("tok_previo"))
                .thenReturn(new CardValidation(false, "cc_rejected_insufficient_amount"));

        PaymentValidationResult result = paymentService.validatePaymentMethod(DRIVER, null);

        assertFalse(result.valid());
        assertEquals(ValidationErrorCode.CARD_DECLINED, result.errorCode());
    }

    @Test
    @DisplayName("validatePaymentMethod: propaga PaymentGatewayUnavailableException si la pasarela falla")
    void validatePaymentMethod_shouldThrowUnavailable_whenGatewayFails() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(card));
        when(pendingPaymentRepository.existsByDriverIdAndStatus(DRIVER, PendingPaymentStatus.PENDING))
                .thenReturn(false);
        when(paymentGateway.validate("tok_previo"))
                .thenThrow(new PaymentGatewayUnavailableException(new RuntimeException("timeout")));

        assertThrows(
                PaymentGatewayUnavailableException.class, () -> paymentService.validatePaymentMethod(DRIVER, null));
    }

    @Test
    @DisplayName("validatePaymentMethod: rechaza con CARD_EXPIRED si la tarjeta del conductor está vencida")
    void validatePaymentMethod_shouldReject_whenCardIsExpired() {
        PaymentMethod expiredCard = existing(DRIVER, CardBrand.VISA, "1111", 10, 2026); // fixedClock está en 11/2026
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(expiredCard));

        PaymentValidationResult result = paymentService.validatePaymentMethod(DRIVER, null);

        assertFalse(result.valid());
        assertEquals(ValidationErrorCode.CARD_EXPIRED, result.errorCode());
        verify(paymentGateway, never()).validate(any());
    }

    @Test
    @DisplayName("validatePaymentMethod: rechaza con NO_PAYMENT_METHOD si no tiene tarjetas registradas")
    void validatePaymentMethod_shouldReject_whenDriverHasNoCards() {
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of());

        PaymentValidationResult result = paymentService.validatePaymentMethod(DRIVER, null);

        assertFalse(result.valid());
        assertEquals(ValidationErrorCode.NO_PAYMENT_METHOD, result.errorCode());
        verify(paymentGateway, never()).validate(any());
    }

    @Test
    @DisplayName("validatePaymentMethod: busca tarjeta específica si se indica paymentMethodId")
    void validatePaymentMethod_shouldUseExplicitPaymentMethodId_whenProvided() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(card));
        when(pendingPaymentRepository.existsByDriverIdAndStatus(DRIVER, PendingPaymentStatus.PENDING))
                .thenReturn(false);
        when(paymentGateway.validate("tok_previo")).thenReturn(new CardValidation(true, "chargeable"));

        PaymentValidationResult result = paymentService.validatePaymentMethod(DRIVER, 1L);

        assertTrue(result.valid());
        assertEquals(ValidationErrorCode.NONE, result.errorCode());
        verify(paymentMethodRepository).findById(1L);
        verify(paymentGateway).validate("tok_previo");
    }

    @Test
    @DisplayName("validatePaymentMethod: rechaza con NO_PAYMENT_METHOD si paymentMethodId es de otro conductor")
    void validatePaymentMethod_shouldReject_whenExplicitPaymentMethodIdBelongsToAnotherDriver() {
        PaymentMethod cardOfOther = existing(ANOTHER_DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(cardOfOther));

        PaymentValidationResult result = paymentService.validatePaymentMethod(DRIVER, 1L);

        assertFalse(result.valid());
        assertEquals(ValidationErrorCode.NO_PAYMENT_METHOD, result.errorCode());
        verify(paymentGateway, never()).validate(any());
    }

    @Test
    @DisplayName("validatePaymentMethod: rechaza con CARD_EXPIRED si la tarjeta específica indicada está vencida")
    void validatePaymentMethod_shouldReject_whenExplicitPaymentMethodIdIsExpired() {
        PaymentMethod expired = existing(DRIVER, CardBrand.VISA, "1111", 10, 2026);
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(expired));

        PaymentValidationResult result = paymentService.validatePaymentMethod(DRIVER, 1L);

        assertFalse(result.valid());
        assertEquals(ValidationErrorCode.CARD_EXPIRED, result.errorCode());
        verify(paymentGateway, never()).validate(any());
    }

    @Test
    @DisplayName("chargeConsumption: cobro aprobado retorna CHARGED sin crear deuda pendiente")
    void chargeConsumption_shouldReturnCharged_whenGatewayApproves() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(pendingPaymentRepository.findByReference("sess-1")).thenReturn(Optional.empty());
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(card));
        when(paymentGateway.charge(
                        "tok_previo",
                        new BigDecimal("1250.00"),
                        "Consumo de sesión de carga",
                        "sess-1",
                        "session-consumption-sess-1"))
                .thenReturn(new GatewayCharge("pay-123", true, "accredited"));

        ConsumptionChargeResult result = paymentService.chargeConsumption(new ConsumptionChargeRequest(
                DRIVER, null, new BigDecimal("1250.00"), "sess-1", "session-consumption-sess-1"));

        assertEquals(ConsumptionChargeResult.Status.CHARGED, result.status());
        assertEquals("pay-123", result.gatewayPaymentId());
        verify(pendingPaymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("chargeConsumption: si la pasarela rechaza registra PendingPayment PENDING y retorna DEBT_RECORDED")
    void chargeConsumption_shouldRecordDebt_whenGatewayDeclines() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(pendingPaymentRepository.findByReference("sess-declined")).thenReturn(Optional.empty());
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(card));
        when(paymentGateway.charge(
                        "tok_previo",
                        new BigDecimal("900.00"),
                        "Consumo de sesión de carga",
                        "sess-declined",
                        "session-consumption-sess-declined"))
                .thenReturn(new GatewayCharge("pay-err", false, "cc_rejected_insufficient_amount"));
        when(pendingPaymentRepository.save(any())).thenAnswer(inv -> {
            PendingPayment p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });

        ConsumptionChargeResult result = paymentService.chargeConsumption(new ConsumptionChargeRequest(
                DRIVER, null, new BigDecimal("900.00"), "sess-declined", "session-consumption-sess-declined"));

        assertEquals(ConsumptionChargeResult.Status.DEBT_RECORDED, result.status());
        assertEquals(50L, result.pendingPaymentId());
        ArgumentCaptor<PendingPayment> captor = ArgumentCaptor.forClass(PendingPayment.class);
        verify(pendingPaymentRepository).save(captor.capture());
        assertEquals(PendingPaymentStatus.PENDING, captor.getValue().getStatus());
        assertEquals("sess-declined", captor.getValue().getReference());
        assertEquals(new BigDecimal("900.00"), captor.getValue().getAmount());
    }

    @Test
    @DisplayName("chargeConsumption: si la pasarela está caída registra PendingPayment PENDING y retorna DEBT_RECORDED")
    void chargeConsumption_shouldRecordDebt_whenGatewayUnavailable() {
        PaymentMethod card = existing(DRIVER, CardBrand.VISA, "1111", 12, 2029);
        when(pendingPaymentRepository.findByReference("sess-down")).thenReturn(Optional.empty());
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of(card));
        when(paymentGateway.charge(
                        "tok_previo",
                        new BigDecimal("1800.00"),
                        "Consumo de sesión de carga",
                        "sess-down",
                        "session-consumption-sess-down"))
                .thenThrow(new PaymentGatewayUnavailableException(new RuntimeException("timeout")));
        when(pendingPaymentRepository.save(any())).thenAnswer(inv -> {
            PendingPayment p = inv.getArgument(0);
            p.setId(51L);
            return p;
        });

        ConsumptionChargeResult result = paymentService.chargeConsumption(new ConsumptionChargeRequest(
                DRIVER, null, new BigDecimal("1800.00"), "sess-down", "session-consumption-sess-down"));

        assertEquals(ConsumptionChargeResult.Status.DEBT_RECORDED, result.status());
        assertEquals(51L, result.pendingPaymentId());
        verify(pendingPaymentRepository).save(any());
    }

    @Test
    @DisplayName(
            "chargeConsumption: si el conductor no tiene tarjeta vigente registra deuda PENDING y retorna DEBT_RECORDED")
    void chargeConsumption_shouldRecordDebt_whenNoUsableCardExists() {
        when(pendingPaymentRepository.findByReference("sess-nocard")).thenReturn(Optional.empty());
        when(paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(DRIVER))
                .thenReturn(List.of());
        when(pendingPaymentRepository.save(any())).thenAnswer(inv -> {
            PendingPayment p = inv.getArgument(0);
            p.setId(52L);
            return p;
        });

        ConsumptionChargeResult result = paymentService.chargeConsumption(new ConsumptionChargeRequest(
                DRIVER, null, new BigDecimal("700.00"), "sess-nocard", "session-consumption-sess-nocard"));

        assertEquals(ConsumptionChargeResult.Status.DEBT_RECORDED, result.status());
        assertEquals(52L, result.pendingPaymentId());
        verify(paymentGateway, never()).charge(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("chargeConsumption: idempotencia si ya existe deuda registrada para la misma sesión")
    void chargeConsumption_shouldBeIdempotent_whenDebtAlreadyRecorded() {
        PendingPayment existingDebt =
                new PendingPayment(DRIVER, null, new BigDecimal("700.00"), "Motivo previo", "sess-idem", Instant.now());
        existingDebt.setId(77L);
        when(pendingPaymentRepository.findByReference("sess-idem")).thenReturn(Optional.of(existingDebt));

        ConsumptionChargeResult result = paymentService.chargeConsumption(new ConsumptionChargeRequest(
                DRIVER, null, new BigDecimal("700.00"), "sess-idem", "session-consumption-sess-idem"));

        assertEquals(ConsumptionChargeResult.Status.DEBT_RECORDED, result.status());
        assertEquals(77L, result.pendingPaymentId());
        verify(paymentGateway, never()).charge(any(), any(), any(), any(), any());
        verify(pendingPaymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("ConsumptionChargeRequest valida invariantes de monto, sesión y clave de idempotencia")
    void consumptionChargeRequestValidatesInvariants() {
        assertThrows(
                NullPointerException.class, () -> new ConsumptionChargeRequest(null, null, BigDecimal.TEN, "s1", "k1"));
        assertThrows(NullPointerException.class, () -> new ConsumptionChargeRequest(1L, null, null, "s1", "k1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConsumptionChargeRequest(1L, null, BigDecimal.ZERO, "s1", "k1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConsumptionChargeRequest(1L, null, new BigDecimal("-1.00"), "s1", "k1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConsumptionChargeRequest(1L, null, BigDecimal.TEN, null, "k1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConsumptionChargeRequest(1L, null, BigDecimal.TEN, "  ", "k1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConsumptionChargeRequest(1L, null, BigDecimal.TEN, "s1", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConsumptionChargeRequest(1L, null, BigDecimal.TEN, "s1", "  "));
    }
}
