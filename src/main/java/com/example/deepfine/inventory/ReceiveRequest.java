package com.example.deepfine.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReceiveRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Positive Long quantity) {
    public ReceiveRequest {
        if (name != null) name = name.strip();
    }
}
