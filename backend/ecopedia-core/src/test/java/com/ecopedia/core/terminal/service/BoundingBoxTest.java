package com.ecopedia.core.terminal.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** El recuadro de la primera pasada nunca deja afuera algo que esté adentro del radio. */
class BoundingBoxTest {

    private static final double KM_PER_DEGREE = Math.toRadians(1) * 6371.0;

    /**
     * Un punto justo en el borde del círculo, en las cuatro direcciones, cae adentro del recuadro.
     * El de longitud se mide sobre el paralelo del centro, que es donde un grado mide más.
     */
    @ParameterizedTest(name = "latitud {0}, radio {1} km")
    @CsvSource({"-34.6037, 5", "-34.6037, 100", "0, 50", "-54.8, 30", "60, 200"})
    @DisplayName("Los cuatro puntos del borde del círculo caen adentro del recuadro")
    void containsTheEdgeOfTheCircle(double latitude, double radiusKm) {
        double longitude = -58.38;
        BoundingBox box = BoundingBox.around(latitude, longitude, radiusKm);
        double deltaLat = radiusKm / KM_PER_DEGREE;
        double deltaLon = radiusKm / (KM_PER_DEGREE * Math.cos(Math.toRadians(latitude)));

        assertThat(box.minLatitude()).isLessThanOrEqualTo(latitude - deltaLat);
        assertThat(box.maxLatitude()).isGreaterThanOrEqualTo(latitude + deltaLat);
        assertThat(box.minLongitude()).isLessThanOrEqualTo(longitude - deltaLon);
        assertThat(box.maxLongitude()).isGreaterThanOrEqualTo(longitude + deltaLon);
    }

    @Test
    @DisplayName("Cerca de un polo o cruzando el antimeridiano toma la longitud entera")
    void fallsBackToEveryLongitude() {
        BoundingBox nearThePole = BoundingBox.around(89.99, 10, 50);
        BoundingBox acrossTheDateLine = BoundingBox.around(-17, 179.9, 100);

        assertThat(nearThePole.minLongitude()).isEqualTo(-180);
        assertThat(nearThePole.maxLongitude()).isEqualTo(180);
        assertThat(nearThePole.maxLatitude()).isEqualTo(90);
        assertThat(acrossTheDateLine.minLongitude()).isEqualTo(-180);
        assertThat(acrossTheDateLine.maxLongitude()).isEqualTo(180);
    }
}
