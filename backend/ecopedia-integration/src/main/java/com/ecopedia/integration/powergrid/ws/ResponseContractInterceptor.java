package com.ecopedia.integration.powergrid.ws;

import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.client.support.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.client.support.interceptor.WebServiceValidationException;
import org.springframework.ws.context.MessageContext;
import org.xml.sax.SAXParseException;

/**
 * Valida cada respuesta de la distribuidora contra la copia del contrato que tiene este módulo
 * ({@code wsdl/power-grid.xsd}, el que importa {@code powerGrid.wsdl}) antes de leerla (QA-25).
 *
 * <p><b>Por qué una subclase.</b> El interceptor de Spring-WS, del lado del cliente, ante una
 * respuesta que no cumple el XSD solo deja un WARN y la procesa igual: JAXB la leería a medias y
 * podría salir, por ejemplo, un tope de potencia vacío. Acá se corta con
 * {@link WebServiceValidationException}, que {@link PowerGridWsClient} trata como "la distribuidora
 * no contestó bien": se carga a potencia normal, igual que con la distribuidora caída (ECO-52).
 *
 * <p>Los pedidos no se validan acá: los arma JAXB desde el mismo XSD y la distribuidora ya los valida
 * del otro lado (devuelve un Fault con {@code ValidationError}). Los Fault tampoco: su detalle se lee
 * por el nombre del elemento.
 */
final class ResponseContractInterceptor extends PayloadValidatingInterceptor {

    static final String CONTRACT_SCHEMA = "wsdl/power-grid.xsd";

    private ResponseContractInterceptor() {}

    /** Armado afuera del constructor: los setters son de la clase de Spring-WS y se pueden sobrescribir. */
    static ResponseContractInterceptor create() throws Exception {
        ResponseContractInterceptor interceptor = new ResponseContractInterceptor();
        interceptor.setSchema(new ClassPathResource(CONTRACT_SCHEMA));
        interceptor.setValidateRequest(false);
        interceptor.setValidateResponse(true);
        interceptor.afterPropertiesSet();
        return interceptor;
    }

    @Override
    protected boolean handleResponseValidationErrors(MessageContext messageContext, SAXParseException[] errors) {
        throw new WebServiceValidationException(errors);
    }
}
