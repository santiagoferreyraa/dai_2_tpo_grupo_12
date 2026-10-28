package com.ecopedia.core.terminal.web.dto;

import com.ecopedia.core.terminal.domain.Station;
import java.util.List;

public record StationResponse(
        Long id,
        String name,
        String address,
        double latitude,
        double longitude,
        Long ownerId,
        boolean active,
        List<String> photoUrls,
        String nodeId,
        List<ConnectorResponse> connectors) {

    public static StationResponse fromDomain(Station station) {
        return fromDomain(station, List.of());
    }

    public static StationResponse fromDomain(Station station, List<ConnectorResponse> connectors) {
        return new StationResponse(
                station.getId(),
                station.getName(),
                station.getAddress(),
                station.getLatitude(),
                station.getLongitude(),
                station.getOwnerId(),
                station.isActive(),
                station.getPhotoUrls(),
                station.getNodeId(),
                connectors != null ? connectors : List.of());
    }
}
