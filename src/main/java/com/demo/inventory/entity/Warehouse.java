package com.demo.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    private String id; // Represents the physical warehouse string code (e.g. WH-IND-01) used by other tables

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WarehouseStatus status;

    public enum WarehouseStatus {
        ACTIVE, INACTIVE, MAINTENANCE
    }
}
