package com.ecopedia.core.pricing.domain;

import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Las reglas de Tarificación que no son precios: cuándo es hora pico, cuánto encarece, desde qué
 * potencia una carga es rápida y cómo escala la multa (decididas por Manuel el 31/10).
 *
 * <p>Están en configuración ({@code ecopedia.pricing}) y no en la base porque no las toca nadie desde
 * la aplicación: son del negocio, no del operador. Los precios, en cambio, los cambia el {@code ADMIN}
 * desde la pantalla, y por eso están en {@link NetworkRates}.
 *
 * @param zone la zona en la que se mide la hora pico: la del conductor, no la del servidor.
 * @param fastChargeThresholdKw hasta cuántos kW una carga es normal. Tiene que coincidir con el corte
 *     de ECO-52 en ecopedia-charging.
 * @param peakStartHour y {@code peakEndHour}: la franja pico, {@code [desde, hasta)}, sobre la hora de
 *     inicio de la carga.
 * @param peakMultiplier cuánto se multiplica el precio del kWh en hora pico.
 * @param penaltyEscalationAfterMinutes pasados estos minutos de exceso, la multa escala.
 * @param penaltyEscalationMultiplier por cuánto se multiplica entonces la multa de todos los minutos.
 */
@ConfigurationProperties(prefix = "ecopedia.pricing")
public record PricingRules(
        ZoneId zone,
        BigDecimal fastChargeThresholdKw,
        int peakStartHour,
        int peakEndHour,
        BigDecimal peakMultiplier,
        int penaltyEscalationAfterMinutes,
        BigDecimal penaltyEscalationMultiplier) {

    public PricingRules {
        if (peakStartHour < 0 || peakEndHour > 24 || peakStartHour >= peakEndHour) {
            throw new IllegalStateException("La franja pico tiene que ser [desde, hasta) dentro del día: "
                    + peakStartHour + " a " + peakEndHour);
        }
    }

    /** Normal o rápida, según la potencia entregada. */
    public ChargeSpeed speedFor(BigDecimal deliveredPowerKw) {
        return deliveredPowerKw.compareTo(fastChargeThresholdKw) > 0 ? ChargeSpeed.FAST : ChargeSpeed.NORMAL;
    }
}
