package com.example.appointments;

public record Room(Long id, String name) {
    public Room {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Room name is required");
        }
    }
}