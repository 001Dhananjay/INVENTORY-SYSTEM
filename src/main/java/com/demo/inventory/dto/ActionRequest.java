package com.demo.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActionRequest {
    @NotNull
    private String orderId; // or reservationId
}
