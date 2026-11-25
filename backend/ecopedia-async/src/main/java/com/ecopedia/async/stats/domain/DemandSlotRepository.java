package com.ecopedia.async.stats.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de las franjas de demanda por conector (ECO-301). */
public interface DemandSlotRepository {

    Optional<DemandSlot> findByConnectorIdAndSlotStart(Long connectorId, Instant slotStart);

    DemandSlot save(DemandSlot slot);

    /** Las franjas del conector que se abrieron sin estación porque core no respondía. */
    List<DemandSlot> findByConnectorIdAndStationIdIsNull(Long connectorId);

    /** Las franjas que empiezan en {@code [from, to)}, de toda la red. */
    List<DemandSlot> findInRange(Instant from, Instant to);

    /** Las franjas que empiezan en {@code [from, to)}, solo de las estaciones del operador. */
    List<DemandSlot> findByOwnerInRange(Long ownerId, Instant from, Instant to);
}
