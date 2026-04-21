package com.demo.inventory.controller;

import com.demo.inventory.dto.ActionRequest;
import com.demo.inventory.dto.AdjustStockRequest;
import com.demo.inventory.dto.ReservationRequest;
import com.demo.inventory.dto.ReservationResponse;
import com.demo.inventory.dto.GenericResponse;
import com.demo.inventory.entity.InventoryItem;
import com.demo.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory API", description = "Endpoints for managing reservations and stock across warehouses")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        String reservationId = inventoryService.reserveStock(request);
        return ResponseEntity.ok(ReservationResponse.builder()
                .status("RESERVED")
                .reservationId(reservationId)
                .build());
    }

    @PostMapping("/release")
    public ResponseEntity<GenericResponse> release(@Valid @RequestBody ActionRequest request) {
        inventoryService.releaseStock(request.getOrderId());
        return ResponseEntity.ok(GenericResponse.builder()
                .status("SUCCESS")
                .message("Reservation " + request.getOrderId() + " released successfully")
                .build());
    }

    @PostMapping("/confirm")
    public ResponseEntity<GenericResponse> confirm(@Valid @RequestBody ActionRequest request) {
        inventoryService.confirmStock(request.getOrderId());
        return ResponseEntity.ok(GenericResponse.builder()
                .status("SUCCESS")
                .message("Reservation " + request.getOrderId() + " confirmed successfully")
                .build());
    }

    @PostMapping("/adjust")
    public ResponseEntity<GenericResponse> adjust(@Valid @RequestBody AdjustStockRequest request) {
        inventoryService.adjustStock(request.getProductId(), request.getWarehouseId(), request.getQuantity());
        return ResponseEntity.ok(GenericResponse.builder()
                .status("SUCCESS")
                .message("Stock adjusted successfully for " + request.getProductId())
                .build());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<List<InventoryItem>> getByProduct(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getProductStock(productId));
    }

    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<InventoryItem>> getByWarehouse(@PathVariable String warehouseId) {
        return ResponseEntity.ok(inventoryService.getWarehouseStock(warehouseId));
    }
}
