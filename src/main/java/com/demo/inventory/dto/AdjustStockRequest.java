package com.demo.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdjustStockRequest {
    @NotNull
    private String productId;
    
    @NotNull
    private String warehouseId;
    
    @NotNull
    private Integer quantity; // positive for IN, negative for OUT
}
