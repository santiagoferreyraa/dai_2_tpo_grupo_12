package com.ecopedia.simulation.paymentgateway.domain;

import java.util.Map;
import java.util.Optional;

/**
 * Las únicas tarjetas que acepta la pasarela: un catálogo fijo de números de prueba.
 *
 * <p><b>Por qué un catálogo y no cualquier número válido.</b> Es una pasarela de mentira, y con un
 * catálogo cerrado una tarjeta real no puede ni entrar: si alguien escribe la suya en la demo, se
 * rechaza antes de tokenizar. Es lo que hacen los entornos de prueba de las pasarelas reales, y
 * es una línea de defensa más encima de no guardar el número (que tampoco se guarda).
 *
 * <p>Los números son los de prueba de siempre —el {@code 4242 4242 4242 4242}, el
 * {@code 4111 1111 1111 1111}—, y todos pasan el dígito verificador, así que el formulario de
 * Ecopedia no tiene que saber que existen. La lista para la demo está en el README.
 */
public final class TestCardCatalog {

    public record TestCard(CardBrand brand, CardBehavior behavior) {}

    private static final Map<String, TestCard> CARDS = Map.of(
            "4242424242424242", new TestCard(CardBrand.VISA, CardBehavior.APPROVE),
            "4111111111111111", new TestCard(CardBrand.VISA, CardBehavior.APPROVE),
            "5555555555554444", new TestCard(CardBrand.MASTERCARD, CardBehavior.APPROVE),
            "378282246310005", new TestCard(CardBrand.AMEX, CardBehavior.APPROVE),
            "4000000000000002", new TestCard(CardBrand.VISA, CardBehavior.DECLINE),
            "4000000000009995", new TestCard(CardBrand.VISA, CardBehavior.INSUFFICIENT_FUNDS),
            "4000000000000077", new TestCard(CardBrand.VISA, CardBehavior.SLOW));

    private TestCardCatalog() {}

    /** La tarjeta de prueba con ese número, sin espacios ni guiones, o vacío si no es de prueba. */
    public static Optional<TestCard> find(String digits) {
        return Optional.ofNullable(CARDS.get(digits));
    }
}
