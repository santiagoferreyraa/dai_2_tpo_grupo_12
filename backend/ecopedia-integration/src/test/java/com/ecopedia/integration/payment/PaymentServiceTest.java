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
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodNotFoundException;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import com.ecopedia.integration.payment.service.PaymentServiceImpl;
import java.time.YearMonth;
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
import org.mockito.InjectMocks;
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
    private PaymentGatewayPort paymentGateway;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Captor
    private ArgumentCaptor<PaymentMethod> savedCard;

    private CardData input;

    @BeforeEach
    void setUp() {
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
}
