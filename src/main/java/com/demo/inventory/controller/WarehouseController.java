package com.demo.inventory.controller;

import com.demo.inventory.entity.Warehouse;
import com.demo.inventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouse Metadata API", description = "Endpoints for managing physical warehouse locations and status")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    public ResponseEntity<Warehouse> createOrUpdateWarehouse(@RequestBody Warehouse warehouse) {
        return ResponseEntity.ok(warehouseService.createOrUpdate(warehouse));
    }

    @GetMapping
    public ResponseEntity<List<Warehouse>> getAllWarehouses() {
        return ResponseEntity.ok(warehouseService.getAllWarehouses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Warehouse> getWarehouseById(@PathVariable String id) {
        return ResponseEntity.ok(warehouseService.getWarehouseById(id));
    }
}
