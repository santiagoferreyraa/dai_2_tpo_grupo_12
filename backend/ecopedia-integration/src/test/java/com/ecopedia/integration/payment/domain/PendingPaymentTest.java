package com.ecopedia.integration.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingPaymentTest {

    @Test
    @DisplayName("Crea un pago pendiente con fecha de creación explícita")
    void createsPendingPaymentWithExplicitCreatedAt() {
        Instant fixedDate = Instant.parse("2026-11-06T12:00:00Z");
        PendingPayment payment = new PendingPayment(
                10L, 20L, new BigDecimal("1500.50"), "Sesión de carga impaga", "session-123", fixedDate);

        assertThat(payment.getDriverId()).isEqualTo(10L);
        assertThat(payment.getPaymentMethodId()).isEqualTo(20L);
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("1500.50"));
        assertThat(payment.getReason()).isEqualTo("Sesión de carga impaga");
        assertThat(payment.getReference()).isEqualTo("session-123");
        assertThat(payment.getStatus()).isEqualTo(PendingPaymentStatus.PENDING);
        assertThat(payment.isPending()).isTrue();
        assertThat(payment.getCreatedAt()).isEqualTo(fixedDate);
        assertThat(payment.getSettledAt()).isNull();
    }

    @Test
    @DisplayName("Crea un pago pendiente sin fecha de creación y usa el instante actual")
    void createsPendingPaymentWithNullCreatedAtDefaultsToNow() {
        Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);
        PendingPayment payment =
                new PendingPayment(10L, null, BigDecimal.TEN, "Reserva no presentada", "booking-456", null);
        Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);

        assertThat(payment.getPaymentMethodId()).isNull();
        assertThat(payment.getCreatedAt()).isNotNull().isBetween(before, after);
        assertThat(payment.isPending()).isTrue();
    }

    @Test
    @DisplayName("Valida campos obligatorios rechazando nulos con NullPointerException")
    void validatesRequiredFields() {
        Instant now = Instant.now();
        BigDecimal amount = BigDecimal.TEN;

        assertThatThrownBy(() -> new PendingPayment(null, 1L, amount, "r", "ref", now))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("driverId");

        assertThatThrownBy(() -> new PendingPayment(1L, 1L, null, "r", "ref", now))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");

        assertThatThrownBy(() -> new PendingPayment(1L, 1L, amount, null, "ref", now))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");

        assertThatThrownBy(() -> new PendingPayment(1L, 1L, amount, "r", null, now))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reference");
    }

    @Test
    @DisplayName("Saldar con fecha explícita actualiza el estado y fecha de liquidación")
    void marksSettledWithExplicitDate() {
        PendingPayment payment = new PendingPayment(10L, 20L, BigDecimal.TEN, "reason", "ref", Instant.now());
        Instant settledAt = Instant.parse("2026-11-06T15:30:00Z");

        payment.markSettled(settledAt);

        assertThat(payment.getStatus()).isEqualTo(PendingPaymentStatus.SETTLED);
        assertThat(payment.isPending()).isFalse();
        assertThat(payment.getSettledAt()).isEqualTo(settledAt);
    }

    @Test
    @DisplayName("Saldar con fecha nula toma el instante actual")
    void marksSettledWithNullDateDefaultsToNow() {
        PendingPayment payment = new PendingPayment(10L, 20L, BigDecimal.TEN, "reason", "ref", Instant.now());
        Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);

        payment.markSettled(null);
        Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);

        assertThat(payment.getStatus()).isEqualTo(PendingPaymentStatus.SETTLED);
        assertThat(payment.isPending()).isFalse();
        assertThat(payment.getSettledAt()).isNotNull().isBetween(before, after);
    }

    @Test
    @DisplayName("Soporta constructor por defecto y setters requeridos por JPA")
    void supportsNoArgConstructorAndSetters() {
        PendingPayment payment = new PendingPayment();
        Instant now = Instant.now();

        payment.setId(99L);
        payment.setDriverId(10L);
        payment.setPaymentMethodId(20L);
        payment.setAmount(BigDecimal.ONE);
        payment.setReason("Ajuste");
        payment.setReference("ref-99");
        payment.setStatus(PendingPaymentStatus.SETTLED);
        payment.setCreatedAt(now);
        payment.setSettledAt(now);
        payment.setVersion(1L);

        assertThat(payment.getId()).isEqualTo(99L);
        assertThat(payment.getDriverId()).isEqualTo(10L);
        assertThat(payment.getPaymentMethodId()).isEqualTo(20L);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(payment.getReason()).isEqualTo("Ajuste");
        assertThat(payment.getReference()).isEqualTo("ref-99");
        assertThat(payment.getStatus()).isEqualTo(PendingPaymentStatus.SETTLED);
        assertThat(payment.isPending()).isFalse();
        assertThat(payment.getCreatedAt()).isEqualTo(now);
        assertThat(payment.getSettledAt()).isEqualTo(now);
        assertThat(payment.getVersion()).isEqualTo(1L);
    }
}
