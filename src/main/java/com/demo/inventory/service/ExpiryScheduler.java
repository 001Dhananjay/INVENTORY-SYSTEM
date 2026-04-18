package com.demo.inventory.service;

import com.demo.inventory.entity.StockReservation;
import com.demo.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiryScheduler {

    private final StockReservationRepository reservationRepository;
    private final InventoryService inventoryService;

    @Scheduled(fixedRateString = "60000") // Run every 60 seconds
    public void releaseExpiredReservations() {
        log.info("Checking for expired reservations...");
        List<StockReservation> expiredReservations = reservationRepository.findByStatusAndExpiresAtBefore(
                StockReservation.ReservationStatus.ACTIVE, LocalDateTime.now());

        for (StockReservation reservation : expiredReservations) {
            try {
                inventoryService.processRelease(
                        reservation.getOrderId(), 
                        StockReservation.ReservationStatus.EXPIRED);
                log.info("Successfully released expired reservation for order: {}", reservation.getOrderId());
            } catch (Exception e) {
                log.error("Failed to release expired reservation: {}", reservation.getOrderId(), e);
            }
        }
    }
}
