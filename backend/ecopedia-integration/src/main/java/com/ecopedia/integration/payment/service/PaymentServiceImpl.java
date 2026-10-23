package com.ecopedia.integration.payment.service;

import com.ecopedia.integration.payment.domain.CardData;
import com.ecopedia.integration.payment.domain.PaymentGatewayPort;
import com.ecopedia.integration.payment.domain.PaymentMethod;
import com.ecopedia.integration.payment.domain.PaymentMethodRepository;
import com.ecopedia.integration.payment.domain.PaymentService;
import com.ecopedia.integration.payment.domain.TokenizedCard;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementación del componente {@code Pagos} para las operaciones de RF02 (ECO-26). */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    /**
     * Tope de tarjetas vigentes por conductor.
     *
     * <p>No lo pide el requisito. Está porque el alta es gratis y no tiene ningún costo para quien
     * la hace: sin un tope, una pantalla con un botón que repite la llamada llena la tabla sin que
     * nada la frene. Diez es holgado para el caso real —una personal, una del trabajo— y sigue
     * siendo un número que un conductor de verdad no alcanza por accidente.
     */
    private static final int MAXIMUM_ACTIVE_CARDS = 10;

    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentGatewayPort paymentGateway;

    public PaymentServiceImpl(PaymentMethodRepository paymentMethodRepository, PaymentGatewayPort paymentGateway) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentGateway = paymentGateway;
    }

    @Override
    public PaymentMethod registerCard(Long driverId, CardData data) {
        List<PaymentMethod> current = paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(driverId);

        if (current.size() >= MAXIMUM_ACTIVE_CARDS) {
            throw new IllegalArgumentException(
                    "Llegaste al máximo de " + MAXIMUM_ACTIVE_CARDS + " tarjetas. Eliminá alguna para agregar otra");
        }

        /*
         * La tokenización va PRIMERO, y el orden no es casual: si la pasarela rechaza la tarjeta,
         * no queremos haber escrito nada. Al revés —guardar y después tokenizar— dejaría filas sin
         * token cada vez que un número viene mal tipeado, que es el error más común de esta
         * pantalla.
         *
         * Es también el único punto del sistema por el que pasa el número completo. De acá sale un
         * TokenizedCard y el número queda atrás.
         */
        TokenizedCard tokenized = paymentGateway.tokenize(data);

        rejectDuplicate(current, tokenized, data);

        PaymentMethod card = new PaymentMethod();
        card.setDriverId(driverId);
        card.setBrand(tokenized.brand());
        card.setLastFour(tokenized.lastFour());
        card.setGatewayToken(tokenized.gatewayToken());
        card.setExpiryMonth(data.expiryMonth());
        card.setExpiryYear(data.expiryYear());
        card.setLabel(normalizedLabel(data.label()));
        card.setActive(true);

        return paymentMethodRepository.save(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethod> listCards(Long driverId) {
        return paymentMethodRepository.findByDriverIdAndActiveTrueOrderByCreatedAtDesc(driverId);
    }

    @Override
    public void removeCard(Long driverId, Long cardId) {
        PaymentMethod card = paymentMethodRepository
                .findById(cardId)
                .filter(found -> {
                    boolean mine = found.getDriverId().equals(driverId);
                    if (!mine) {
                        // La respuesta no lo dice, el log sí (SEC-06).
                        log.warn(
                                "Acceso denegado: el conductor {} quiso dar de baja la tarjeta {}, que es de otro",
                                driverId,
                                cardId);
                    }
                    return mine;
                })
                .filter(PaymentMethod::isActive)
                /*
                 * "No encontrada" también cuando existe pero es de otro, y es a propósito. Un
                 * mensaje distinto para "existe pero no es tuya" le confirma a cualquiera que ese
                 * id corresponde a una tarjeta real: probando ids consecutivos se puede contar
                 * cuántas tarjetas tiene la plataforma. Como no hay nada que el dueño legítimo
                 * pueda hacer distinto según el caso, las dos situaciones se contestan igual.
                 */
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la tarjeta con ID: " + cardId));

        // Baja lógica: los cobros y las deudas que cuelgan del token tienen que seguir existiendo.
        card.setActive(false);
        paymentMethodRepository.save(card);
    }

    /**
     * Rechaza registrar dos veces la misma tarjeta.
     *
     * <p><b>La comparación es aproximada, y no se puede hacer mejor.</b> Lo exacto sería comparar
     * números, pero el número no se guarda (RF02) y la pasarela emite un token distinto en cada
     * tokenización. Así que se compara por lo que sí tenemos: misma marca, mismos
     * cuatro últimos dígitos y mismo vencimiento. Dos tarjetas distintas que coincidan en las tres
     * cosas existen en teoría; en el conjunto de tarjetas de una persona, no.
     *
     * <p>El costo de equivocarse es asimétrico y por eso se elige este lado: un falso positivo le
     * niega un alta a alguien que puede usar la tarjeta que ya tiene registrada, mientras que un
     * falso negativo le deja dos filas idénticas en la pantalla sin forma de saber cuál es cuál.
     */
    private void rejectDuplicate(List<PaymentMethod> current, TokenizedCard tokenized, CardData data) {
        boolean alreadyRegistered = current.stream()
                .anyMatch(card -> card.getBrand() == tokenized.brand()
                        && card.getLastFour().equals(tokenized.lastFour())
                        && card.getExpiryMonth() == data.expiryMonth()
                        && card.getExpiryYear() == data.expiryYear());

        if (alreadyRegistered) {
            throw new IllegalArgumentException("Esa tarjeta ya está registrada");
        }
    }

    /** Una etiqueta en blanco es lo mismo que no haber puesto ninguna: se guarda como ausente. */
    private String normalizedLabel(String label) {
        if (label == null || label.isBlank()) return null;
        return label.trim();
    }
}
