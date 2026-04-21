package com.demo.inventory.service;

import com.demo.inventory.entity.StockLedger;
import com.demo.inventory.repository.StockLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final StockLedgerRepository ledgerRepository;

    @Transactional(propagation = Propagation.MANDATORY) // Must be called within an existing transaction
    public void recordLedgerEntry(String productId, String warehouseId, StockLedger.LedgerType type, Integer quantity, String referenceId) {
        StockLedger ledger = StockLedger.builder()
                .productId(productId)
                .warehouseId(warehouseId)
                .type(type)
                .quantity(quantity)
                .referenceId(referenceId)
                .timestamp(LocalDateTime.now())
                .build();
        
        ledgerRepository.save(ledger);
    }
}
