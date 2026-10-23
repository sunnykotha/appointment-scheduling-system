package com.example.appointments;

public record Provider(Long id, String name, String specialty) {
    public Provider {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Provider name is required");
        }
        if (specialty == null || specialty.isBlank()) {
            throw new IllegalArgumentException("Provider specialty is required");
        }
    }
}