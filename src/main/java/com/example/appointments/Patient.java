package com.example.appointments;

public record Patient(Long id, String name) {
    public Patient {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Patient name is required");
        }
    }
}