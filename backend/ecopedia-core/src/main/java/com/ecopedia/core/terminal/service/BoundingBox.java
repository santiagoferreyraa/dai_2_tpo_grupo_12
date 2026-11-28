package com.ecopedia.core.terminal.service;

/**
 * El recuadro de latitud y longitud que contiene al círculo de la búsqueda: la primera pasada, la
 * que resuelve la base con el índice. Es más grande que el círculo a propósito —las esquinas las
 * recorta después la distancia de verdad—, y nunca más chico: una estación adentro del radio
 * siempre cae adentro del recuadro.
 *
 * <p>Un grado de latitud mide lo mismo en todos lados; uno de longitud se achica hacia los polos,
 * con el coseno de la latitud. Cerca de un polo, o si el recuadro cruzaría el antimeridiano, se
 * toma la longitud entera: más filas para recortar, nunca una estación perdida.
 */
record BoundingBox(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {

    /** Kilómetros de un grado de latitud, con el mismo radio terrestre que la distancia (6371 km). */
    private static final double KM_PER_DEGREE = Math.toRadians(1) * 6371.0;

    static BoundingBox around(double latitude, double longitude, double radiusKm) {
        double deltaLatitude = radiusKm / KM_PER_DEGREE;
        double minLatitude = Math.max(-90, latitude - deltaLatitude);
        double maxLatitude = Math.min(90, latitude + deltaLatitude);

        // El coseno más chico del recuadro es el del borde más cercano al polo: ahí un grado mide menos.
        double widestLatitude = Math.max(Math.abs(minLatitude), Math.abs(maxLatitude));
        double cosine = Math.cos(Math.toRadians(widestLatitude));
        if (cosine < 1e-6) return new BoundingBox(minLatitude, maxLatitude, -180, 180);

        double deltaLongitude = radiusKm / (KM_PER_DEGREE * cosine);
        double minLongitude = longitude - deltaLongitude;
        double maxLongitude = longitude + deltaLongitude;
        if (deltaLongitude >= 180 || minLongitude < -180 || maxLongitude > 180) {
            return new BoundingBox(minLatitude, maxLatitude, -180, 180);
        }
        return new BoundingBox(minLatitude, maxLatitude, minLongitude, maxLongitude);
    }
}
