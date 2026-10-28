package com.ecopedia.simulation.powergrid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * La distribuidora eléctrica provincial simulada.
 *
 * <p><b>No es parte de Ecopedia.</b> Es el sistema legado con el que Ecopedia se integra por SOAP
 * (RNF04): corre como proceso propio, en su puerto, y publica un servicio con su WSDL. Antes de
 * habilitar una carga rápida, RedEléctrica le pregunta si el nodo de la estación aguanta la
 * potencia pedida (RF15), y la distribuidora contesta según un catálogo fijo de nodos de prueba.
 *
 * <p>Por qué SOAP y no REST está en ARQUITECTURA §3.1: es un ente regulado, con contrato rígido y
 * tipado fuerte, que no expone APIs modernas. Lo construimos nosotros porque la consigna pide
 * publicar un servicio SOAP con su WSDL, no solo consumirlo.
 */
@SpringBootApplication
public class PowerGridSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PowerGridSimulatorApplication.class, args);
    }
}
