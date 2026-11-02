package com.ecopedia.core.pricing.service;

import com.ecopedia.core.pricing.domain.ChargeBreakdown;
import com.ecopedia.core.pricing.domain.ChargeSpeed;
import com.ecopedia.core.pricing.domain.NetworkRates;
import com.ecopedia.core.pricing.domain.NetworkRatesRepository;
import com.ecopedia.core.pricing.domain.PricingRules;
import com.ecopedia.core.pricing.domain.PricingService;
import com.ecopedia.core.pricing.domain.StationPricing;
import com.ecopedia.core.pricing.domain.StationPricingRepository;
import com.ecopedia.core.pricing.domain.strategy.OccupancyPenalty;
import com.ecopedia.core.pricing.domain.strategy.PricingStrategy;
import com.ecopedia.core.pricing.domain.strategy.PricingStrategySelector;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.TerminalService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de Tarificación (stateless, RF06 y RF14) con el modelo del 31/10.
 *
 * <p>Calcular una carga son cuatro preguntas, y cada una tiene un solo dueño:
 *
 * <ol>
 *   <li><b>¿Normal o rápida?</b> La potencia entregada contra el corte de ECO-52 ({@link PricingRules}).
 *   <li><b>¿Cuánto vale el kWh de ese tipo?</b> Los precios de la red ({@link NetworkRates}).
 *   <li><b>¿Plana o pico?</b> La elige {@link PricingStrategySelector} según la hora de inicio y la estación.
 *   <li><b>¿Hay multa?</b> {@link OccupancyPenalty}, que se suma con cualquier estrategia.
 * </ol>
 *
 * <p>Las estaciones se consultan a Terminales por su interfaz Java: los dos componentes viven en core.
 */
@Service
@Transactional
public class PricingServiceImpl implements PricingService {

    private final NetworkRatesRepository networkRatesRepository;
    private final StationPricingRepository stationPricingRepository;
    private final TerminalService terminalService;
    private final PricingRules rules;
    private final PricingStrategySelector strategySelector;
    private final OccupancyPenalty occupancyPenalty;
    private final Clock clock;

    public PricingServiceImpl(
            NetworkRatesRepository networkRatesRepository,
            StationPricingRepository stationPricingRepository,
            TerminalService terminalService,
            PricingRules rules,
            Clock clock) {
        this.networkRatesRepository = networkRatesRepository;
        this.stationPricingRepository = stationPricingRepository;
        this.terminalService = terminalService;
        this.rules = rules;
        this.strategySelector = new PricingStrategySelector(rules);
        this.occupancyPenalty = new OccupancyPenalty(rules);
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkRates getNetworkRates() {
        return networkRatesRepository
                .findById(NetworkRates.SINGLETON_ID)
                // La migración siembra la fila; si falta, la base está rota y no hay precio que inventar.
                .orElseThrow(() -> new IllegalStateException("Faltan los precios de la red (tabla network_rates)"));
    }

    @Override
    public NetworkRates updateNetworkRates(
            BigDecimal normalKwhRate, BigDecimal fastKwhRate, BigDecimal excessPenaltyPerMin) {
        Instant now = clock.instant();
        NetworkRates rates = networkRatesRepository
                .findById(NetworkRates.SINGLETON_ID)
                .orElseGet(() -> new NetworkRates(normalKwhRate, fastKwhRate, excessPenaltyPerMin, now));
        rates.update(normalKwhRate, fastKwhRate, excessPenaltyPerMin, now);
        return networkRatesRepository.save(rates);
    }

    @Override
    @Transactional(readOnly = true)
    public StationPricing getStationPricing(Long stationId) {
        // Que la estación exista lo dice Terminales: si no, IllegalArgumentException.
        terminalService.getStation(stationId);
        return stationPricingRepository.findById(stationId).orElseGet(() -> StationPricing.defaultsFor(stationId));
    }

    @Override
    public StationPricing updateStationPricing(Long stationId, BigDecimal depositAmount, boolean peakPricingEnabled) {
        terminalService.getStation(stationId);
        Instant now = clock.instant();
        StationPricing pricing = stationPricingRepository
                .findById(stationId)
                .orElseGet(() -> new StationPricing(stationId, depositAmount, peakPricingEnabled, now));
        pricing.update(depositAmount, peakPricingEnabled, now);
        return stationPricingRepository.save(pricing);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateDeposit(Long connectorId) {
        return stationPricingOf(terminalService.getConnector(connectorId)).getDepositAmount();
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeBreakdown estimateCost(Long connectorId, BigDecimal estimatedKwh, Instant start) {
        Connector connector = terminalService.getConnector(connectorId);
        StationPricing station = stationPricingOf(connector);
        ChargeBreakdown estimate = price(estimatedKwh, connector.getMaxPowerKw(), start, 0, station);

        // Regla RF06: el estimado nunca puede ser menor a la seña cobrada.
        if (estimate.total().compareTo(station.getDepositAmount()) < 0) {
            throw new IllegalArgumentException("El importe estimado (" + estimate.total()
                    + ") no supera el monto de la seña (" + station.getDepositAmount() + ")");
        }
        return estimate;
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeBreakdown calculateRealCost(
            Long connectorId, BigDecimal kwhConsumed, BigDecimal deliveredPowerKw, Instant start, long excessMinutes) {
        Connector connector = terminalService.getConnector(connectorId);
        return price(kwhConsumed, deliveredPowerKw, start, excessMinutes, stationPricingOf(connector));
    }

    /** Las cuatro preguntas, en orden. Ver el javadoc de la clase. */
    private ChargeBreakdown price(
            BigDecimal kwh, BigDecimal powerKw, Instant start, long excessMinutes, StationPricing station) {
        NetworkRates rates = getNetworkRates();
        ChargeSpeed speed = rules.speedFor(powerKw);
        BigDecimal baseRate = rates.kwhRateFor(speed);
        PricingStrategy strategy = strategySelector.select(start, station);

        BigDecimal energy = strategy.energyCost(kwh, baseRate);
        BigDecimal penalty = occupancyPenalty.of(excessMinutes, rates.getExcessPenaltyPerMin());
        return new ChargeBreakdown(
                speed, strategy.isPeak(), strategy.effectiveKwhRate(baseRate), energy, penalty, energy.add(penalty));
    }

    private StationPricing stationPricingOf(Connector connector) {
        Long stationId = connector.getStation().getId();
        return stationPricingRepository.findById(stationId).orElseGet(() -> StationPricing.defaultsFor(stationId));
    }
}
