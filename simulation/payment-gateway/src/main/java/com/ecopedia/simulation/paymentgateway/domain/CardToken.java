package com.ecopedia.simulation.paymentgateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Una tarjeta tokenizada: lo que la pasarela guarda de ella.
 *
 * <p><b>No tiene el número, y no por descuido.</b> Para cobrar con el token alcanza con saber la
 * marca, el vencimiento y cómo se comporta la tarjeta de prueba; el número se usa para decidir eso
 * en el alta y se descarta ahí mismo. Si alguien se lleva el archivo de la base, no hay ninguna
 * tarjeta que sacar. Es la misma regla que Ecopedia cumple del otro lado (RF02).
 *
 * <p>El token es aleatorio, no derivado del número: un token calculado a partir del número es el
 * número otra vez, disfrazado.
 */
@Entity
@Table(name = "card_tokens")
public class CardToken {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardBrand brand;

    @Column(name = "last_four", nullable = false)
    private String lastFour;

    @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardBehavior behavior;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CardToken() {}

    public CardToken(
            CardBrand brand, String lastFour, int expiryMonth, int expiryYear, CardBehavior behavior, Instant now) {
        this.id = "tok_" + UUID.randomUUID().toString().replace("-", "");
        this.brand = brand;
        this.lastFour = lastFour;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.behavior = behavior;
        this.createdAt = now;
    }

    /** Vence el último día del mes impreso: una 09/26 sirve todo septiembre de 2026. */
    public boolean isExpiredAt(YearMonth now) {
        return YearMonth.of(expiryYear, expiryMonth).isBefore(now);
    }

    public String getId() {
        return id;
    }

    public CardBrand getBrand() {
        return brand;
    }

    public String getLastFour() {
        return lastFour;
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public CardBehavior getBehavior() {
        return behavior;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
