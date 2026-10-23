package com.example.appointments;

import java.util.Set;

public record AppointmentType(
        Long id,
        String name,
        int durationMinutes,
        Set<String> allowedSpecialties) {

    public AppointmentType {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Appointment type name is required");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        allowedSpecialties = allowedSpecialties == null
                ? Set.of()
                : Set.copyOf(allowedSpecialties);
    }

    public boolean allowsSpecialty(String specialty) {
        return allowedSpecialties.isEmpty()
                || allowedSpecialties.contains(specialty);
    }
}