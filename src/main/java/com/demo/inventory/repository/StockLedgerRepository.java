package com.demo.inventory.repository;

import com.demo.inventory.entity.StockLedger;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StockLedgerRepository extends JpaRepository<StockLedger, Integer> {
}
