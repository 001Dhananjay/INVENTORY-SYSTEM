package com.demo.inventory.repository;

import com.demo.inventory.entity.StockReservation;
import com.demo.inventory.entity.StockReservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, String> {
    Optional<StockReservation> findByOrderId(String orderId);
    
    List<StockReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime now);
}
