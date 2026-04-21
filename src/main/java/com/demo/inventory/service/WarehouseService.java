package com.demo.inventory.service;

import com.demo.inventory.entity.Warehouse;
import com.demo.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional
    public Warehouse createOrUpdate(Warehouse warehouse) {
        return warehouseRepository.save(warehouse);
    }

    @Transactional(readOnly = true)
    public List<Warehouse> getAllWarehouses() {
        return warehouseRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Warehouse getWarehouseById(String id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found for id: " + id));
    }
}
