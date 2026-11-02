package com.ecopedia.core.terminal.domain;

import java.util.List;

/**
 * Datos con los que se da de alta o se edita una estación (RF04).
 *
 * <p>No es el DTO del controlador: es el tipo de entrada de la capa de negocio. La capa de
 * presentación traduce su propio DTO a esto, y así el contrato REST puede cambiar sin
 * arrastrar a {@link TerminalService}.
 *
 * @param name nombre visible de la estación
 * @param address dirección postal
 * @param latitude latitud en grados decimales
 * @param longitude longitud en grados decimales
 * @param photoUrls fotos de la estación; puede venir vacía
 * @param nodeId nodo de la distribuidora (RF15); {@code null} si no tiene
 */
public record StationData(
        String name, String address, double latitude, double longitude, List<String> photoUrls, String nodeId) {}
