package com.example.appointments;

public record Equipment(Long id, String name) {
    public Equipment {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Equipment name is required");
        }
    }
}