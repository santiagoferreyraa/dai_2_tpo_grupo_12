package com.ecopedia.core.pricing.web.dto;

/**
 * El importe más grande que entra en las columnas de Tarificación ({@code NUMERIC(10, 2)}: ocho
 * dígitos enteros y dos decimales). Un pedido que lo supera da 400 en vez de un 500 al guardar.
 */
final class PricingLimits {

    static final String MAX_AMOUNT = "99999999.99";

    private PricingLimits() {}
}
