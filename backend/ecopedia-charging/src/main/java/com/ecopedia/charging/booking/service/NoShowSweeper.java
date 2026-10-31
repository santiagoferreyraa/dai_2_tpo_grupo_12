package com.ecopedia.charging.booking.service;

import com.ecopedia.charging.booking.domain.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marca las incomparecencias cada tanto (RF09, ECO-57).
 *
 * <p>Es solo el reloj: la regla vive en {@link BookingService#expireNoShows()}, que se puede probar
 * sin esperar a ningún reloj. Un minuto de intervalo alcanza: la tolerancia es de 15, así que el
 * conector se libera a lo sumo un minuto más tarde de lo que podría.
 */
@Component
public class NoShowSweeper {

    private static final Logger log = LoggerFactory.getLogger(NoShowSweeper.class);

    private final BookingService bookingService;

    public NoShowSweeper(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(
            fixedDelayString = "${ecopedia.booking.no-show-sweep-interval}",
            initialDelayString = "${ecopedia.booking.no-show-sweep-interval}")
    void sweep() {
        try {
            bookingService.expireNoShows();
        } catch (RuntimeException failure) {
            // La base caída no tiene que matar el reloj: el próximo barrido lo vuelve a intentar.
            log.warn("No se pudo barrer las incomparecencias: {}", failure.getMessage());
        }
    }
}
