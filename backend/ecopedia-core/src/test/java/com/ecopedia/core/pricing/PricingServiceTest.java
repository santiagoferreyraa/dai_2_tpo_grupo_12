package com.ecopedia.core.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecopedia.core.pricing.domain.ChargeBreakdown;
import com.ecopedia.core.pricing.domain.ChargeSpeed;
import com.ecopedia.core.pricing.domain.NetworkRates;
import com.ecopedia.core.pricing.domain.NetworkRatesRepository;
import com.ecopedia.core.pricing.domain.PricingRules;
import com.ecopedia.core.pricing.domain.StationPricing;
import com.ecopedia.core.pricing.domain.StationPricingRepository;
import com.ecopedia.core.pricing.domain.strategy.OccupancyPenalty;
import com.ecopedia.core.pricing.domain.strategy.PricingStrategySelector;
import com.ecopedia.core.pricing.service.PricingServiceImpl;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.TerminalService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tarificación con el modelo del 31/10, sin levantar Spring: qué estrategia toca en cada caso y cuánto
 * sale una carga.
 *
 * <p>Las horas se escriben en UTC a propósito, que es como corre el servidor en AWS: las 21:00 UTC son las
 * 18:00 en Buenos Aires. Si el cálculo usara la zona del servidor, los bordes de la franja darían al revés.
 */
class PricingServiceTest {

    private static final PricingRules RULES = new PricingRules(
            ZoneId.of("America/Argentina/Buenos_Aires"),
            new BigDecimal("22"),
            18,
            22,
            new BigDecimal("1.2"),
            15,
            new BigDecimal("1.5"));

    private static final StationPricing WITH_PEAK = new StationPricing(7L, new BigDecimal("5000"), true, Instant.EPOCH);
    private static final StationPricing WITHOUT_PEAK =
            new StationPricing(7L, new BigDecimal("5000"), false, Instant.EPOCH);

    /** Un instante dado en hora de Buenos Aires, por ejemplo "17:59": es UTC-3, así que se le suman 3 h. */
    private static Instant argentina(String hhmm) {
        return Instant.parse("2026-11-02T" + hhmm + ":00Z").plusSeconds(3 * 3600);
    }

    @Nested
    class StrategySelection {

        private final PricingStrategySelector selector = new PricingStrategySelector(RULES);

        @ParameterizedTest(name = "inicio a las {0} → pico: {1}")
        @CsvSource({"17:59, false", "18:00, true", "21:59, true", "22:00, false", "03:00, false"})
        @DisplayName("Decide la hora de INICIO, en hora argentina: [18:00, 22:00)")
        void peakDependsOnTheStartHour(String start, boolean peak) {
            assertThat(selector.select(argentina(start), WITH_PEAK).isPeak()).isEqualTo(peak);
        }

        @Test
        @DisplayName("Una estación sin el check de hora pico cobra tarifa plana también a las 19")
        void stationWithoutPeakIsAlwaysFlat() {
            assertThat(selector.select(argentina("19:00"), WITHOUT_PEAK).isPeak())
                    .isFalse();
        }

        @Test
        @DisplayName("Las 18:00 de Buenos Aires son las 21:00 UTC: la zona del servidor no cuenta")
        void usesTheDriverTimeZoneNotTheServers() {
            assertThat(selector.isPeakHour(Instant.parse("2026-11-02T21:00:00Z")))
                    .isTrue();
            assertThat(selector.isPeakHour(Instant.parse("2026-11-02T18:00:00Z")))
                    .isFalse();
        }
    }

    @Nested
    class SpeedAndPenalty {

        @ParameterizedTest(name = "{0} kW → {1}")
        @CsvSource({"7.2, NORMAL", "22, NORMAL", "22.01, FAST", "150, FAST"})
        @DisplayName("Normal hasta 22 kW inclusive; rápida por encima, como en ECO-52")
        void speedByDeliveredPower(String kw, ChargeSpeed speed) {
            assertThat(RULES.speedFor(new BigDecimal(kw))).isEqualTo(speed);
        }

        @ParameterizedTest(name = "{0} min de exceso → $ {1}")
        @CsvSource({"0, 0.00", "10, 2000.00", "15, 3000.00", "16, 4800.00"})
        @DisplayName("La multa: $ 200 por minuto, y pasados 15 minutos todos los minutos a ×1,5")
        void penaltyEscalatesAfterFifteenMinutes(long minutes, String expected) {
            assertThat(new OccupancyPenalty(RULES).of(minutes, new BigDecimal("200")))
                    .isEqualByComparingTo(expected);
        }
    }

    @Nested
    class Charges {

        private PricingServiceImpl service;
        private Connector connector;

        @BeforeEach
        void setUp() {
            NetworkRatesRepository rates = mock(NetworkRatesRepository.class);
            when(rates.findById(NetworkRates.SINGLETON_ID))
                    .thenReturn(Optional.of(new NetworkRates(
                            new BigDecimal("100"), new BigDecimal("150"), new BigDecimal("200"), Instant.EPOCH)));
            StationPricingRepository stations = mock(StationPricingRepository.class);
            when(stations.findById(7L)).thenReturn(Optional.of(WITH_PEAK));

            Station station = mock(Station.class);
            when(station.getId()).thenReturn(7L);
            connector = mock(Connector.class);
            when(connector.getStation()).thenReturn(station);
            when(connector.getMaxPowerKw()).thenReturn(new BigDecimal("150"));
            TerminalService terminals = mock(TerminalService.class);
            when(terminals.getConnector(12L)).thenReturn(connector);

            service = new PricingServiceImpl(
                    rates, stations, terminals, RULES, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("Carga rápida que empieza 21:30 y termina después de las 22: toda a precio pico, $ 180 el kWh")
        void fastChargeStartedInPeakIsAllPeak() {
            ChargeBreakdown cost =
                    service.calculateRealCost(12L, new BigDecimal("10"), new BigDecimal("150"), argentina("21:30"), 0);

            assertThat(cost.speed()).isEqualTo(ChargeSpeed.FAST);
            assertThat(cost.peak()).isTrue();
            assertThat(cost.kwhRate()).isEqualByComparingTo("180");
            assertThat(cost.total()).isEqualByComparingTo("1800");
        }

        @Test
        @DisplayName("Carga normal que empieza 17:30 y sigue después de las 18: toda a precio normal, $ 100 el kWh")
        void normalChargeStartedBeforePeakIsAllFlat() {
            ChargeBreakdown cost =
                    service.calculateRealCost(12L, new BigDecimal("10"), new BigDecimal("22"), argentina("17:30"), 0);

            assertThat(cost.speed()).isEqualTo(ChargeSpeed.NORMAL);
            assertThat(cost.peak()).isFalse();
            assertThat(cost.total()).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("Un conector rápido que la red bajó a 22 kW se cobra como carga normal")
        void downgradedFastConnectorIsChargedAsNormal() {
            ChargeBreakdown cost =
                    service.calculateRealCost(12L, new BigDecimal("10"), new BigDecimal("22"), argentina("12:00"), 0);

            assertThat(cost.speed()).isEqualTo(ChargeSpeed.NORMAL);
            assertThat(cost.kwhRate()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("Pico y exceso se aplican juntos: energía a precio pico más la multa escalada")
        void peakAndPenaltyApplyTogether() {
            ChargeBreakdown cost =
                    service.calculateRealCost(12L, new BigDecimal("10"), new BigDecimal("22"), argentina("19:00"), 16);

            assertThat(cost.peak()).isTrue();
            assertThat(cost.energyCost()).isEqualByComparingTo("1200"); // 10 kWh × $ 120
            assertThat(cost.penalty()).isEqualByComparingTo("4800"); // 16 min × $ 200 × 1,5
            assertThat(cost.total()).isEqualByComparingTo("6000");
        }

        @Test
        @DisplayName("La seña es la de la estación del conector")
        void depositIsTheStations() {
            assertThat(service.calculateDeposit(12L)).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("El estimado usa la potencia máxima del conector y no puede quedar por debajo de la seña")
        void estimateUsesTheConnectorPowerAndKeepsTheFloor() {
            ChargeBreakdown estimate = service.estimateCost(12L, new BigDecimal("40"), argentina("12:00"));
            assertThat(estimate.speed()).isEqualTo(ChargeSpeed.FAST);
            assertThat(estimate.total()).isEqualByComparingTo("6000");

            assertThatThrownBy(() -> service.estimateCost(12L, new BigDecimal("10"), argentina("12:00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
