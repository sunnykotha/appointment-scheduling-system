package com.example.appointments;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;

public record Appointment(
        Long id,
        Patient patient,
        Provider provider,
        Room room,
        Equipment equipment,
        AppointmentType appointmentType,
        LocalDateTime start,
        int bufferMinutes,
        boolean cancelled,
        String recurrenceSeriesId,
        boolean reschedulingRequired,
        ZonedDateTime zonedStart) {

    public Appointment(
            Long id,
            Patient patient,
            Provider provider,
            Room room,
            Equipment equipment,
            AppointmentType appointmentType,
            LocalDateTime start,
            int bufferMinutes) {
        this(id, patient, provider, room, equipment, appointmentType,
                start, bufferMinutes, false, null, false, null);
    }

    public Appointment(
            Long id,
            Patient patient,
            Provider provider,
            Room room,
            Equipment equipment,
            AppointmentType appointmentType,
            LocalDateTime start,
            int bufferMinutes,
            boolean cancelled) {
        this(id, patient, provider, room, equipment, appointmentType,
                start, bufferMinutes, cancelled, null, false, null);
    }

    public Appointment(
            Long id,
            Patient patient,
            Provider provider,
            Room room,
            Equipment equipment,
            AppointmentType appointmentType,
            LocalDateTime start,
            int bufferMinutes,
            boolean cancelled,
            String recurrenceSeriesId) {
        this(id, patient, provider, room, equipment, appointmentType,
                start, bufferMinutes, cancelled, recurrenceSeriesId, false, null);
    }

    public Appointment(
            Long id,
            Patient patient,
            Provider provider,
            Room room,
            Equipment equipment,
            AppointmentType appointmentType,
            LocalDateTime start,
            int bufferMinutes,
            boolean cancelled,
            String recurrenceSeriesId,
            boolean reschedulingRequired) {
        this(id, patient, provider, room, equipment, appointmentType,
                start, bufferMinutes, cancelled, recurrenceSeriesId,
                reschedulingRequired, null);
    }

    public Appointment(
            Long id,
            Patient patient,
            Provider provider,
            Room room,
            Equipment equipment,
            AppointmentType appointmentType,
            ZonedDateTime zonedStart,
            int bufferMinutes) {
        this(id, patient, provider, room, equipment, appointmentType,
                requireZonedStart(zonedStart).toLocalDateTime(),
                bufferMinutes, false, null, false, zonedStart);
    }

    public Appointment(
            Long id,
            Patient patient,
            Provider provider,
            Room room,
            Equipment equipment,
            AppointmentType appointmentType,
            ZonedDateTime zonedStart,
            int bufferMinutes,
            boolean cancelled,
            String recurrenceSeriesId,
            boolean reschedulingRequired) {
        this(id, patient, provider, room, equipment, appointmentType,
                requireZonedStart(zonedStart).toLocalDateTime(),
                bufferMinutes, cancelled, recurrenceSeriesId,
                reschedulingRequired, zonedStart);
    }

    public Appointment {
        Objects.requireNonNull(patient, "Patient is required");
        Objects.requireNonNull(provider, "Provider is required");
        Objects.requireNonNull(room, "Room is required");
        Objects.requireNonNull(appointmentType, "Appointment type is required");
        Objects.requireNonNull(start, "Start time is required");

        if (bufferMinutes < 0) {
            throw new IllegalArgumentException("Buffer cannot be negative");
        }

        if (zonedStart != null
                && !zonedStart.toLocalDateTime().equals(start)) {
            throw new IllegalArgumentException(
                    "Zoned start must match the local start time");
        }

        if (!appointmentType.allowsSpecialty(provider.specialty())) {
            throw new IllegalArgumentException(
                    "Provider specialty is not allowed for this appointment type");
        }
    }

    private static ZonedDateTime requireZonedStart(ZonedDateTime start) {
        return Objects.requireNonNull(start, "Zoned start time is required");
    }

    public TimeSlot getAppointmentSlot() {
        if (zonedStart != null) {
            return new TimeSlot(
                    zonedStart,
                    zonedStart.plusMinutes(appointmentType.durationMinutes()));
        }

        return new TimeSlot(
                start,
                start.plusMinutes(appointmentType.durationMinutes()));
    }

    public TimeSlot getReservedSlot() {
        int reservedMinutes =
                appointmentType.durationMinutes() + bufferMinutes;

        if (zonedStart != null) {
            return new TimeSlot(
                    zonedStart,
                    zonedStart.plusMinutes(reservedMinutes));
        }

        return new TimeSlot(
                start,
                start.plusMinutes(reservedMinutes));
    }

    public Appointment cancel() {
        return new Appointment(
                id, patient, provider, room, equipment,
                appointmentType, start, bufferMinutes,
                true, recurrenceSeriesId, reschedulingRequired, zonedStart);
    }

    public Appointment markForRescheduling() {
        return new Appointment(
                id, patient, provider, room, equipment,
                appointmentType, start, bufferMinutes,
                cancelled, recurrenceSeriesId, true, zonedStart);
    }
}