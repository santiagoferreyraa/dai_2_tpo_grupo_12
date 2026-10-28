package com.ecopedia.simulation.powergrid.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.ws.soap.SoapMessageCreationException;

/**
 * Lo que se mira de un pedido antes de que Spring-WS lo parsee: el tamaño y que sea un mensaje SOAP.
 *
 * <p><b>El tamaño.</b> Un pedido legítimo pesa menos de 2 KB, cabecera de seguridad incluida. Sin
 * tope, un XML de cientos de megas se parsea entero en memoria antes de que nadie mire si tiene
 * sentido. El cuerpo se lee hasta el tope ({@code simulation.power-grid.max-request-bytes}) y, si lo
 * pasa, vuelve un 413 sin parsear nada; vale igual con {@code Content-Length} o enviado por partes.
 *
 * <p><b>Los mensajes que no se pueden leer</b> —XML roto, o con un {@code DOCTYPE}— los rechaza el
 * parser de SAAJ, y el {@code DOCTYPE} siempre: así no hay entidades externas que puedan leer
 * archivos del disco (XXE). Sin este filtro, ese rechazo salía como un 500 genérico con la pila en
 * el log como ERROR; ahora sale como un Fault {@code Client} con un 400, que es lo que es: un pedido
 * mal formado, no una falla de la distribuidora.
 */
@Component
public class IncomingMessageFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IncomingMessageFilter.class);

    private static final String MALFORMED_FAULT =
            """
            <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"><SOAP-ENV:Body>\
            <SOAP-ENV:Fault><faultcode>SOAP-ENV:Client</faultcode>\
            <faultstring xml:lang="en">Malformed SOAP message</faultstring></SOAP-ENV:Fault>\
            </SOAP-ENV:Body></SOAP-ENV:Envelope>""";

    private final int maxBytes;

    public IncomingMessageFilter(@Value("${simulation.power-grid.max-request-bytes}") int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = readUpTo(request.getInputStream(), maxBytes);
        if (body == null) {
            log.warn("Pedido rechazado por tamaño: más de {} bytes", maxBytes);
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }

        try {
            chain.doFilter(new BufferedRequest(request, body), response);
        } catch (ServletException failure) {
            if (!isUnreadableMessage(failure) || response.isCommitted()) throw failure;
            log.warn("Pedido rechazado por mal formado: {}", rootMessage(failure));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/xml;charset=UTF-8");
            response.getOutputStream().write(MALFORMED_FAULT.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** El cuerpo entero, o {@code null} si pasa el tope. Nunca lee más que el tope y un byte. */
    private static byte[] readUpTo(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > limit) return null;
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean isUnreadableMessage(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SoapMessageCreationException) return true;
        }
        return false;
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage();
    }

    /** El pedido, con el cuerpo que ya se leyó. */
    private static final class BufferedRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return in.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lectura asíncrona no soportada");
                }
            };
        }
    }
}
