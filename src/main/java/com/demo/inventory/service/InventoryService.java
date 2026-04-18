package com.demo.inventory.service;

import com.demo.inventory.dto.ReservationRequest;
import com.demo.inventory.entity.InventoryItem;
import com.demo.inventory.entity.StockLedger;
import com.demo.inventory.entity.StockReservation;
import com.demo.inventory.exception.InsufficientStockException;
import com.demo.inventory.repository.InventoryItemRepository;
import com.demo.inventory.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final StockReservationRepository reservationRepository;
    private final LedgerService ledgerService;

    private static final int RESERVATION_TTL_MINUTES = 30;

    @Transactional
    public String reserveStock(ReservationRequest request) {
        log.info("Starting atomic reservation for order: {}", request.getOrderId());
        
        // Sorting items could help prevent deadlocks in high concurrency, omitted for brevity.
        for (ReservationRequest.Item itemDto : request.getItems()) {
            InventoryItem inventoryItem = inventoryItemRepository
                    .findByProductIdAndWarehouseIdForUpdate(itemDto.getProductId(), itemDto.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException("Product/Warehouse combination not found"));

            if (inventoryItem.getAvailableStock() < itemDto.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product: " + itemDto.getProductId());
            }

            // Update item stock
            inventoryItem.setReservedStock(inventoryItem.getReservedStock() + itemDto.getQuantity());
            inventoryItemRepository.save(inventoryItem);

            // Create Reservation record
            StockReservation reservation = StockReservation.builder()
                    .id(UUID.randomUUID().toString())
                    .orderId(request.getOrderId())
                    .productId(itemDto.getProductId())
                    .warehouseId(itemDto.getWarehouseId())
                    .quantity(itemDto.getQuantity())
                    .status(StockReservation.ReservationStatus.ACTIVE)
                    .expiresAt(LocalDateTime.now().plusMinutes(RESERVATION_TTL_MINUTES))
                    .build();
            reservationRepository.save(reservation);

            // Add Ledger Entry
            ledgerService.recordLedgerEntry(
                    itemDto.getProductId(),
                    itemDto.getWarehouseId(),
                    StockLedger.LedgerType.RESERVE,
                    itemDto.getQuantity(),
                    request.getOrderId()
            );
        }

        return request.getOrderId();
    }

    @Transactional
    public void releaseStock(String orderId) {
        processRelease(orderId, StockReservation.ReservationStatus.CANCELLED);
    }

    @Transactional
    public void confirmStock(String orderId) {
        log.info("Confirming reservation for order: {}", orderId);
        StockReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found for order: " + orderId));

        if (reservation.getStatus() != StockReservation.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation is not ACTIVE. Current status: " + reservation.getStatus());
        }

        reservation.setStatus(StockReservation.ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        InventoryItem item = inventoryItemRepository
                .findByProductIdAndWarehouseIdForUpdate(reservation.getProductId(), reservation.getWarehouseId())
                .orElseThrow();

        item.setReservedStock(item.getReservedStock() - reservation.getQuantity());
        item.setTotalStock(item.getTotalStock() - reservation.getQuantity());
        inventoryItemRepository.save(item);

        ledgerService.recordLedgerEntry(
                reservation.getProductId(),
                reservation.getWarehouseId(),
                StockLedger.LedgerType.CONFIRM,
                reservation.getQuantity(),
                orderId
        );
    }

    @Transactional
    public void processRelease(String orderId, StockReservation.ReservationStatus newStatus) {
        log.info("Releasing reservation for order: {}", orderId);
        StockReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found for order: " + orderId));

        if (reservation.getStatus() != StockReservation.ReservationStatus.ACTIVE) {
            log.warn("Reservation {} is not ACTIVE, nothing to release", orderId);
            return;
        }

        reservation.setStatus(newStatus);
        reservationRepository.save(reservation);

        InventoryItem item = inventoryItemRepository
                .findByProductIdAndWarehouseIdForUpdate(reservation.getProductId(), reservation.getWarehouseId())
                .orElseThrow();

        item.setReservedStock(item.getReservedStock() - reservation.getQuantity());
        inventoryItemRepository.save(item);

        ledgerService.recordLedgerEntry(
                reservation.getProductId(),
                reservation.getWarehouseId(),
                StockLedger.LedgerType.RELEASE,
                reservation.getQuantity(),
                orderId
        );
    }
    
    @Transactional
    public void adjustStock(String productId, String warehouseId, Integer quantity) {
        InventoryItem item = inventoryItemRepository
                .findByProductIdAndWarehouseIdForUpdate(productId, warehouseId)
                .orElseGet(() -> {
                    InventoryItem newItem = InventoryItem.builder()
                            .productId(productId)
                            .warehouseId(warehouseId)
                            .totalStock(0)
                            .reservedStock(0)
                            .lockedStock(0)
                            .build();
                    return inventoryItemRepository.save(newItem);
                });

        if (item.getTotalStock() + quantity < 0) {
            throw new IllegalArgumentException("Total stock cannot be negative");
        }

        item.setTotalStock(item.getTotalStock() + quantity);
        inventoryItemRepository.save(item);

        ledgerService.recordLedgerEntry(
                productId,
                warehouseId,
                quantity >= 0 ? StockLedger.LedgerType.IN : StockLedger.LedgerType.OUT,
                Math.abs(quantity),
                "MANUAL_ADJUSTMENT"
        );
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> getProductStock(String productId) {
        return inventoryItemRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> getWarehouseStock(String warehouseId) {
        return inventoryItemRepository.findByWarehouseId(warehouseId);
    }
}
