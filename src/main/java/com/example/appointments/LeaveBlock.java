package com.example.appointments;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record LeaveBlock(
        Long id,
        Provider provider,
        LocalDateTime start,
        LocalDateTime end,
        String reason) {

    public LeaveBlock {
        Objects.requireNonNull(provider, "Provider is required");
        Objects.requireNonNull(start, "Leave start time is required");
        Objects.requireNonNull(end, "Leave end time is required");

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "Leave end time must be after start time");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Leave reason must not be blank");
        }
    }

    public TimeSlot getSlot() {
        return new TimeSlot(start, end);
    }

    public boolean blocks(Appointment appointment) {
        Objects.requireNonNull(appointment, "Appointment is required");

        return !appointment.cancelled()
                && provider.id() != null
                && provider.id().equals(appointment.provider().id())
                && getSlot().overlaps(appointment.getReservedSlot());
    }

    public List<Appointment> appointmentsToReschedule(
            List<Appointment> appointments) {
        Objects.requireNonNull(appointments, "Appointments are required");

        return appointments.stream()
                .filter(this::blocks)
                .map(Appointment::markForRescheduling)
                .toList();
    }
}