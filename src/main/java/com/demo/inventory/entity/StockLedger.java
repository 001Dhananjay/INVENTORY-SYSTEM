package com.demo.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "stock_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private String warehouseId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LedgerType type;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String referenceId; // orderId or reservationId

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public enum LedgerType {
        IN, OUT, RESERVE, RELEASE, CONFIRM
    }
}
