package com.ecopedia.charging.booking;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.charging.booking.domain.BookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La ruta interna con la que Pagos anula las reservas de una cuenta marcada por deuda (ECO-62): solo
 * con la clave de servicio, nunca con el token de un conductor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InternalBookingApiTest {

    private static final String PATH = "/internal/bookings/debt-cancellations";
    private static final String BODY = "{\"driverId\": 42}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Value("${ecopedia.internal.token}")
    private String serviceToken;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @Test
    @DisplayName("Con la clave de servicio anula las reservas del conductor y dice cuántas")
    void cancelsWithTheServiceToken() throws Exception {
        when(bookingService.cancelConfirmedBookingsForDebt(42L)).thenReturn(2);

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(2));
    }

    @Test
    @DisplayName("Sin la clave: 401 INVALID_SERVICE_TOKEN y no anula nada")
    void rejectsWithoutTheServiceToken() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));

        verify(bookingService, never()).cancelConfirmedBookingsForDebt(anyLong());
    }

    @Test
    @DisplayName("El token válido de un conductor no abre la ruta interna")
    void aDriverTokenDoesNotOpenIt() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 42L, "CONDUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));

        verify(bookingService, never()).cancelConfirmedBookingsForDebt(anyLong());
    }

    @Test
    @DisplayName("Sin conductor en el cuerpo: 400, sin anular nada")
    void rejectsAMissingDriver() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).cancelConfirmedBookingsForDebt(anyLong());
    }
}
