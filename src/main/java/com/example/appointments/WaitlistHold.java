package com.example.appointments;

import java.time.LocalDateTime;
import java.util.Objects;

public record WaitlistHold(
        WaitlistEntry entry,
        Appointment appointment,
        LocalDateTime createdAt,
        LocalDateTime expiresAt) {

    public WaitlistHold {
        Objects.requireNonNull(entry, "Waitlist entry is required");
        Objects.requireNonNull(appointment, "Held appointment is required");
        Objects.requireNonNull(createdAt, "Hold creation time is required");
        Objects.requireNonNull(expiresAt, "Hold expiry time is required");

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "Hold expiry must be after its creation time");
        }

        if (!Objects.equals(
                entry.appointmentType().id(),
                appointment.appointmentType().id())) {
            throw new IllegalArgumentException(
                    "Held appointment type must match the waitlist entry");
        }
    }

    public boolean isActiveAt(LocalDateTime time) {
        Objects.requireNonNull(time, "Current time is required");
        return !time.isBefore(createdAt) && time.isBefore(expiresAt);
    }
}