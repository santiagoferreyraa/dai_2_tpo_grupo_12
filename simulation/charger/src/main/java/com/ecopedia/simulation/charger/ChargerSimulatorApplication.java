package com.ecopedia.simulation.charger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Los cargadores simulados.
 *
 * <p><b>No es parte de Ecopedia.</b> Es el hardware de las estaciones —o, mejor dicho, la central
 * del fabricante que lo controla—, corriendo como proceso propio en su puerto (RNF04). Terminales le
 * da dos órdenes por REST, activar y desactivar un conector, y mientras el conector carga el
 * simulador publica una lectura cada pocos segundos en el tópico {@code telemetry.charging}: kWh
 * entregados, potencia y porcentaje de batería. Esas lecturas son las que van a leer SesionesDeCarga,
 * para cortar al llegar al objetivo y cobrar el consumo, y Estadísticas.
 *
 * <p>Simula también el auto: no hay un vehículo de verdad que informe su batería, así que el
 * simulador la lleva él, con una curva de carga creíble y el tiempo acelerado para que una carga
 * entera entre en una demo. Y se puede romper un conector a propósito, para mostrar qué pasa cuando
 * falla la máquina.
 */
@SpringBootApplication
@EnableScheduling
public class ChargerSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChargerSimulatorApplication.class, args);
    }
}
