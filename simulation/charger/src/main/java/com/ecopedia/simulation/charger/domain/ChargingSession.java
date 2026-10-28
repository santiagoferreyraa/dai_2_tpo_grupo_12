package com.ecopedia.simulation.charger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Una carga en curso: el conector entregando energía y el auto simulado recibiéndola.
 *
 * <p><b>La curva de carga.</b> Hasta el 80 % de la batería, la potencia es la menor entre lo que
 * permite el conector (ya recortado por la distribuidora, si correspondió) y lo que acepta el auto.
 * Desde el 80 % baja en línea recta hasta un 10 % de ese máximo al llegar al 100 %. Es la forma que
 * tiene una carga rápida de verdad, y es lo que hace que "cargar hasta el 80 %" sea la elección
 * habitual: los últimos veinte puntos tardan casi lo mismo que los ochenta anteriores.
 *
 * <p>No es segura para hilos: la protege {@code ChargerSimulator}, que es quien la toca.
 */
public class ChargingSession {

    /** Por encima de esto se da la batería por llena: la curva se acerca al 100 sin tocarlo nunca. */
    private static final double FULL_PERCENT = 99.95;

    private static final double TAPER_FROM_PERCENT = 80;
    private static final double TAPER_FLOOR = 0.1;

    private final String sessionId;
    private final long connectorId;
    private final double maxPowerKw;
    private final double batteryKwh;

    private double batteryPercent;
    private double energyKwh;
    private double lastPowerKw;

    public ChargingSession(
            String sessionId,
            long connectorId,
            double powerLimitKw,
            double vehicleMaxChargeKw,
            double batteryKwh,
            double initialBatteryPercent) {
        this.sessionId = sessionId;
        this.connectorId = connectorId;
        this.maxPowerKw = Math.min(powerLimitKw, vehicleMaxChargeKw);
        this.batteryKwh = batteryKwh;
        this.batteryPercent = initialBatteryPercent;
        this.lastPowerKw = powerAt(initialBatteryPercent);
    }

    /**
     * Avanza la carga {@code minutes} minutos simulados. La potencia del intervalo es la del
     * porcentaje con que empezó, y nunca se entrega más energía de la que le falta a la batería.
     */
    public void advance(double minutes) {
        if (isFull()) return;
        double power = powerAt(batteryPercent);
        double missingKwh = (100 - batteryPercent) / 100 * batteryKwh;
        double delivered = Math.min(power * minutes / 60, missingKwh);

        energyKwh += delivered;
        batteryPercent = Math.min(100, batteryPercent + delivered / batteryKwh * 100);
        if (batteryPercent >= FULL_PERCENT) batteryPercent = 100;
        lastPowerKw = power;
    }

    public boolean isFull() {
        return batteryPercent >= 100;
    }

    private double powerAt(double percent) {
        if (percent < TAPER_FROM_PERCENT) return maxPowerKw;
        double share = (100 - percent) / (100 - TAPER_FROM_PERCENT);
        return maxPowerKw * Math.max(TAPER_FLOOR, share);
    }

    public String sessionId() {
        return sessionId;
    }

    public long connectorId() {
        return connectorId;
    }

    public BigDecimal powerKw() {
        return round(lastPowerKw, 2);
    }

    public BigDecimal energyKwh() {
        return round(energyKwh, 3);
    }

    public BigDecimal batteryPercent() {
        return round(batteryPercent, 1);
    }

    private static BigDecimal round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
