package com.example.deepfine.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReceiveRequest(
        @NotBlank(message = "상품명은 필수입니다.") @Size(max = 100, message = "상품명은 100자 이하여야 합니다.") String name,
        @NotNull(message = "수량은 필수입니다.") @Positive(message = "수량은 양수여야 합니다.") Long quantity) {
    public ReceiveRequest {
        if (name != null) name = name.strip();
    }
}
