package com.example.appointments;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class RecurringAppointmentService {

    private final BookingService bookingService = new BookingService();

    public List<Appointment> schedule(
            Appointment template,
            int occurrenceCount,
            int intervalDays,
            List<Appointment> existing,
            RecurrencePolicy policy) {

        Objects.requireNonNull(template, "Appointment template is required");
        Objects.requireNonNull(existing, "Existing appointments are required");
        Objects.requireNonNull(policy, "Recurrence policy is required");

        if (occurrenceCount <= 0) {
            throw new IllegalArgumentException(
                    "Occurrence count must be positive");
        }

        if (intervalDays <= 0) {
            throw new IllegalArgumentException(
                    "Recurrence interval must be positive");
        }

        if (template.zonedStart() != null) {
            return scheduleZoned(
                    template, occurrenceCount, intervalDays, existing, policy);
        }

        String seriesId = UUID.randomUUID().toString();
        List<Appointment> scheduled = new ArrayList<>();

        for (int i = 0; i < occurrenceCount; i++) {
            LocalDateTime requestedStart =
                    template.start().plusDays((long) i * intervalDays);

            Appointment occurrence =
                    createOccurrence(template, requestedStart, seriesId);

            if (policy == RecurrencePolicy.SHIFT) {
                occurrence = findNextAvailableOccurrence(
                        template, requestedStart, existing, scheduled, seriesId);
                scheduled.add(occurrence);
            } else if (canBook(occurrence, existing, scheduled)) {
                scheduled.add(occurrence);
            } else if (policy == RecurrencePolicy.REJECT) {
                throw new IllegalArgumentException(
                        "Recurring series contains a conflicting occurrence");
            }
        }

        return List.copyOf(scheduled);
    }

    private List<Appointment> scheduleZoned(
            Appointment template,
            int occurrenceCount,
            int intervalDays,
            List<Appointment> existing,
            RecurrencePolicy policy) {

        String seriesId = UUID.randomUUID().toString();
        List<Appointment> scheduled = new ArrayList<>();

        for (int i = 0; i < occurrenceCount; i++) {
            ZonedDateTime requestedStart = template.zonedStart()
                    .plusDays((long) i * intervalDays);

            Appointment occurrence =
                    createZonedOccurrence(template, requestedStart, seriesId);

            if (policy == RecurrencePolicy.SHIFT) {
                occurrence = findNextAvailableZonedOccurrence(
                        template, requestedStart, existing, scheduled, seriesId);
                scheduled.add(occurrence);
            } else if (canBook(occurrence, existing, scheduled)) {
                scheduled.add(occurrence);
            } else if (policy == RecurrencePolicy.REJECT) {
                throw new IllegalArgumentException(
                        "Recurring series contains a conflicting occurrence");
            }
        }

        return List.copyOf(scheduled);
    }

    private Appointment findNextAvailableOccurrence(
            Appointment template,
            LocalDateTime requestedStart,
            List<Appointment> existing,
            List<Appointment> scheduled,
            String seriesId) {

        List<Appointment> appointmentsToCheck =
                new ArrayList<>(existing);
        appointmentsToCheck.addAll(scheduled);

        Appointment candidate =
                createOccurrence(template, requestedStart, seriesId);

        while (!bookingService.canBook(candidate, appointmentsToCheck)) {
            LocalDateTime nextStart = null;

            for (Appointment appointment : appointmentsToCheck) {
                if (!bookingService.canBook(candidate, List.of(appointment))) {
                    LocalDateTime reservedEnd =
                            appointment.getReservedSlot().end();

                    if (nextStart == null || reservedEnd.isAfter(nextStart)) {
                        nextStart = reservedEnd;
                    }
                }
            }

            if (nextStart == null || !nextStart.isAfter(candidate.start())) {
                throw new IllegalStateException(
                        "Unable to find the end of the conflicting reservation");
            }

            candidate = createOccurrence(template, nextStart, seriesId);
        }

        return candidate;
    }

    private Appointment findNextAvailableZonedOccurrence(
            Appointment template,
            ZonedDateTime requestedStart,
            List<Appointment> existing,
            List<Appointment> scheduled,
            String seriesId) {

        List<Appointment> appointmentsToCheck =
                new ArrayList<>(existing);
        appointmentsToCheck.addAll(scheduled);

        Appointment candidate =
                createZonedOccurrence(template, requestedStart, seriesId);

        while (!bookingService.canBook(candidate, appointmentsToCheck)) {
            ZonedDateTime nextStart = null;

            for (Appointment appointment : appointmentsToCheck) {
                if (!bookingService.canBook(candidate, List.of(appointment))) {
                    ZonedDateTime reservedEnd =
                            zonedReservedEnd(appointment, requestedStart.getZone());

                    if (nextStart == null
                            || reservedEnd.toInstant().isAfter(nextStart.toInstant())) {
                        nextStart = reservedEnd;
                    }
                }
            }

            if (nextStart == null
                    || !nextStart.toInstant().isAfter(candidate.zonedStart().toInstant())) {
                throw new IllegalStateException(
                        "Unable to find the end of the conflicting reservation");
            }

            candidate = createZonedOccurrence(template, nextStart, seriesId);
        }

        return candidate;
    }

    private ZonedDateTime zonedReservedEnd(
            Appointment appointment, ZoneId zone) {

        if (appointment.zonedStart() != null) {
            return appointment.zonedStart().plusMinutes(
                    appointment.appointmentType().durationMinutes()
                            + appointment.bufferMinutes());
        }

        return appointment.getReservedSlot().end().atZone(zone);
    }

    private boolean canBook(
            Appointment candidate,
            List<Appointment> existing,
            List<Appointment> scheduled) {

        List<Appointment> appointmentsToCheck =
                new ArrayList<>(existing);
        appointmentsToCheck.addAll(scheduled);

        return bookingService.canBook(candidate, appointmentsToCheck);
    }

    private Appointment createOccurrence(
            Appointment template,
            LocalDateTime start,
            String seriesId) {

        return new Appointment(
                null,
                template.patient(),
                template.provider(),
                template.room(),
                template.equipment(),
                template.appointmentType(),
                start,
                template.bufferMinutes(),
                false,
                seriesId);
    }

    private Appointment createZonedOccurrence(
            Appointment template,
            ZonedDateTime start,
            String seriesId) {

        return new Appointment(
                null,
                template.patient(),
                template.provider(),
                template.room(),
                template.equipment(),
                template.appointmentType(),
                start,
                template.bufferMinutes(),
                false,
                seriesId,
                false);
    }
}