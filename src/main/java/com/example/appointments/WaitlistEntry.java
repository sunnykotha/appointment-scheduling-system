package com.example.appointments;

import java.time.LocalDateTime;
import java.util.Objects;

public record WaitlistEntry(
        Long id,
        Patient patient,
        AppointmentType appointmentType,
        int urgency,
        LocalDateTime addedAt,
        boolean acceptsMornings) {

    public WaitlistEntry(Long id, Patient patient, AppointmentType appointmentType,
            int urgency, LocalDateTime addedAt) {
        this(id, patient, appointmentType, urgency, addedAt, true);
    }

    public WaitlistEntry {
        Objects.requireNonNull(patient, "Patient is required");
        Objects.requireNonNull(appointmentType, "Appointment type is required");
        Objects.requireNonNull(addedAt, "Waitlist entry time is required");
        if (urgency < 0) {
            throw new IllegalArgumentException("Urgency cannot be negative");
        }
    }
}
