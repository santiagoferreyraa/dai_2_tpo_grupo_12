package com.ecopedia.core.pricing;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.pricing.domain.ChargeBreakdown;
import com.ecopedia.core.pricing.domain.ChargeSpeed;
import com.ecopedia.core.pricing.domain.PricingService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas para el endpoint de cálculo del costo real al cerrar una sesión (RF14, ECO-55).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PricingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PricingService pricingService;

    @Test
    @DisplayName("GET /api/pricing/real-cost delega en PricingService y devuelve 200 OK con el desglose y totalCost")
    void calculatesRealCostAndReturnsBreakdown() throws Exception {
        Instant start = Instant.parse("2026-11-10T18:30:00Z");
        ChargeBreakdown breakdown = new ChargeBreakdown(
                ChargeSpeed.FAST,
                true,
                new BigDecimal("180.00"),
                new BigDecimal("3600.00"),
                new BigDecimal("500.00"),
                new BigDecimal("4100.00"));

        when(pricingService.calculateRealCost(7L, new BigDecimal("20.00"), new BigDecimal("150.00"), start, 5L))
                .thenReturn(breakdown);

        mockMvc.perform(get("/api/pricing/real-cost")
                        .param("connectorId", "7")
                        .param("kwhConsumed", "20.00")
                        .param("deliveredPowerKw", "150.00")
                        .param("start", start.toString())
                        .param("excessMinutes", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.energyKwh").value(20.00))
                .andExpect(jsonPath("$.appliedKwhRate").value(180.00))
                .andExpect(jsonPath("$.baseEnergyCost").value(3600.00))
                .andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.excessMinutes").value(5))
                .andExpect(jsonPath("$.excessPenaltyAmount").value(500.00))
                .andExpect(jsonPath("$.totalCost").value(4100.00))
                .andExpect(jsonPath("$.fastTierApplied").value(true))
                .andExpect(jsonPath("$.peakApplied").value(true));

        verify(pricingService).calculateRealCost(7L, new BigDecimal("20.00"), new BigDecimal("150.00"), start, 5L);
    }

    @Test
    @DisplayName("GET /api/pricing/real-cost usa excessMinutes=0 por defecto cuando no se informa")
    void usesZeroExcessMinutesByDefault() throws Exception {
        Instant start = Instant.parse("2026-11-10T10:00:00Z");
        ChargeBreakdown breakdown = new ChargeBreakdown(
                ChargeSpeed.NORMAL,
                false,
                new BigDecimal("100.00"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                new BigDecimal("1000.00"));

        when(pricingService.calculateRealCost(7L, new BigDecimal("10.00"), new BigDecimal("22.00"), start, 0L))
                .thenReturn(breakdown);

        mockMvc.perform(get("/api/pricing/real-cost")
                        .param("connectorId", "7")
                        .param("kwhConsumed", "10.00")
                        .param("deliveredPowerKw", "22.00")
                        .param("start", start.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excessMinutes").value(0))
                .andExpect(jsonPath("$.totalCost").value(1000.00))
                .andExpect(jsonPath("$.fastTierApplied").value(false))
                .andExpect(jsonPath("$.peakApplied").value(false));
    }
}
