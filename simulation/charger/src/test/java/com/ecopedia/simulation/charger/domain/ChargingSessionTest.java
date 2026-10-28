package com.ecopedia.simulation.charger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La curva de carga, sin Spring. */
class ChargingSessionTest {

    @Test
    @DisplayName("Hasta el 80 %, carga a la menor potencia entre la del conector y la que acepta el auto")
    void constantPowerUntilEighty() {
        ChargingSession session = new ChargingSession("s", 1, 150, 100, 60, 20);

        session.advance(6);

        // 100 kW durante 6 minutos: 10 kWh, un sexto de la batería.
        assertThat(session.powerKw()).isEqualByComparingTo("100");
        assertThat(session.energyKwh()).isEqualByComparingTo("10.000");
        assertThat(session.batteryPercent()).isEqualByComparingTo("36.7");
    }

    @Test
    @DisplayName("Desde el 80 % la potencia baja, y a mitad de camino al 100 entrega la mitad")
    void tapersAfterEighty() {
        ChargingSession session = new ChargingSession("s", 1, 50, 50, 60, 90);

        session.advance(1);

        assertThat(session.powerKw()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("Nunca entrega más de lo que le falta a la batería, ni pasa del 100 %")
    void neverOverfills() {
        ChargingSession session = new ChargingSession("s", 1, 350, 350, 10, 79);

        for (int minute = 0; minute < 120; minute++) session.advance(1);

        assertThat(session.isFull()).isTrue();
        assertThat(session.batteryPercent()).isEqualByComparingTo("100");
        assertThat(session.energyKwh()).isEqualByComparingTo("2.100");
    }
}
