package com.ecopedia.charging.session.domain;

/**
 * Estado operativo del conector gestionado localmente por SesionesDeCarga (RNF06, ECO-60).
 */
public enum ConnectorOperationalStatus {
    AVAILABLE,
    OCCUPIED,
    OUT_OF_SERVICE
}
