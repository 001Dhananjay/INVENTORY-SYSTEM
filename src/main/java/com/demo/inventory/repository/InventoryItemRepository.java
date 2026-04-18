package com.demo.inventory.repository;

import com.demo.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Integer> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.productId = :productId AND i.warehouseId = :warehouseId")
    Optional<InventoryItem> findByProductIdAndWarehouseIdForUpdate(@Param("productId") String productId, @Param("warehouseId") String warehouseId);

    Optional<InventoryItem> findByProductIdAndWarehouseId(String productId, String warehouseId);

    List<InventoryItem> findByProductId(String productId);

    List<InventoryItem> findByWarehouseId(String warehouseId);
}
