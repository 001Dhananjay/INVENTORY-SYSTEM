package com.demo.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(name = "inventory_items", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"productId", "warehouseId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private String warehouseId;

    @Column(nullable = false)
    private Integer totalStock = 0;

    @Column(nullable = false)
    private Integer reservedStock = 0;

    @Column(nullable = false)
    private Integer lockedStock = 0;

    @Version
    private Integer version;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    public Integer getAvailableStock() {
        return totalStock - reservedStock - lockedStock;
    }
}
