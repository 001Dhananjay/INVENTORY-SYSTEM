package com.demo.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class ReservationRequest {
    @NotNull
    private String orderId;
    

    @NotEmpty
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private String productId;
        
        @NotNull
        private String warehouseId;
        
        @NotNull
        private Integer quantity;
    }
}
