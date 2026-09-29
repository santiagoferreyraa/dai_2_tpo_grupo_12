package com.ecopedia.core.terminal.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interfaz del componente <i>Terminales</i>, stateless. Ver ARQUITECTURA_ECOPEDIA.md §2.3.
 *
 * <p><b>Esta interfaz es el componente</b>, no el controlador REST: el controlador es un
 * transporte que expone parte de ella. Por eso {@link #getConnector(Long)} no tiene ruta
 * HTTP — lo consumen Reservas y SesionesDeCarga desde adentro del sistema.
 *
 * <p>Es stateless porque ninguna de estas operaciones deja nada colgado entre llamadas: lo
 * que persiste es dominio guardado en la base, no estado conversacional que el contenedor
 * tenga que sostener.
 *
 * <p>Las operaciones están declaradas y sin implementar a propósito: el commit semilla fija
 * las firmas para que los carriles de datos, negocio, presentación y frontend puedan avanzar
 * en paralelo contra el mismo contrato.
 *
 * <p>Cubre RF04, RF05, RF07 y la mitad de RF03.
 */
public interface TerminalService {

    /**
     * Alta de una estación. La publica el operador que la registra (RF04), y ese operador
     * queda como su dueño: es quien después la puede editar y dar de baja.
     */
    Station createStation(Long ownerId, StationData data);

    /**
     * Si la estación es del usuario. Falso también si la estación no existe.
     *
     * <p>La consulta la autorización del ABM antes de dejar pasar a un operador: un {@code CPO}
     * solo toca lo suyo. Ver {@code TerminalAccess}.
     */
    boolean isStationOwner(Long stationId, Long userId);

    /** Si el conector cuelga de una estación del usuario. Falso también si no existe. */
    boolean isConnectorOwner(Long connectorId, Long userId);

    /** Edición de los datos de una estación existente (RF04). */
    Station updateStation(Long stationId, StationData data);

    /** Baja lógica de una estación: deja de aparecer en la búsqueda (RF04). */
    void deactivateStation(Long stationId);

    /**
     * Cuelga un conector nuevo de una estación existente (RF05).
     *
     * <p>Está separada de {@link #configureConnector(Long, ConnectorType, BigDecimal)} a
     * propósito: cada una recibe el identificador de una sola clase de cosa —acá una estación,
     * allá un conector—. Una única operación que resolviera "si el id corresponde a un conector
     * lo edito, si no creo uno" es ambigua, porque estaciones y conectores tienen secuencias de
     * identificadores independientes: apenas existe el conector 1, la estación 1 ya no puede
     * recibir un segundo conector.
     */
    Connector addConnector(Long stationId, ConnectorType connectorType, BigDecimal maxPowerKw);

    /** Parametriza tipo y potencia máxima de un conector que ya existe (RF05). */
    Connector configureConnector(Long connectorId, ConnectorType connectorType, BigDecimal maxPowerKw);

    /**
     * Cambia el estado operativo de un conector (RF05).
     *
     * <p>Tiene dos consumidores muy distintos: el operador desde el ABM, y SesionesDeCarga
     * cuando ocupa y libera el conector.
     */
    void changeOperationalStatus(Long connectorId, OperationalStatus operationalStatus);

    /** Búsqueda geolocalizada con filtros de conector, potencia y disponibilidad (RF07). */
    List<StationResult> search(SearchCriteria criteria);

    /** Devuelve un conector por su identificador. */
    Connector getConnector(Long connectorId);

    /** Devuelve todos los conectores registrados en la plataforma. */
    List<Connector> getAllConnectors();

    /** Devuelve todos los conectores pertenecientes a una estación determinada. */
    List<Connector> getConnectorsByStation(Long stationId);

    /** Devuelve una estación por su identificador. */
    Station getStation(Long stationId);

    /** Devuelve todas las estaciones activas. */
    List<Station> getAllStations();

    /** Elimina un conector por su identificador. */
    void removeConnector(Long connectorId);
}
