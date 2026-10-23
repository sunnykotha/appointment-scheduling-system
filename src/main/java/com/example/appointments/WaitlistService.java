package com.example.appointments;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class WaitlistService {

    private static final long HOLD_MINUTES = 15;

    private final List<WaitlistHold> activeHolds = new ArrayList<>();

    public Optional<WaitlistEntry> selectNext(
            AppointmentType appointmentType,
            List<WaitlistEntry> entries) {

        Objects.requireNonNull(appointmentType, "Appointment type is required");
        Objects.requireNonNull(entries, "Waitlist entries are required");

        return entries.stream()
                .filter(entry -> sameId(
                        entry.appointmentType().id(), appointmentType.id()))
                .sorted(
                        Comparator.comparingInt(WaitlistEntry::urgency).reversed()
                                .thenComparing(WaitlistEntry::addedAt))
                .findFirst();
    }

    public Optional<WaitlistHold> handleCancellation(
            Appointment cancelledAppointment,
            List<WaitlistEntry> entries,
            LocalDateTime cancelledAt) {

        Objects.requireNonNull(
                cancelledAppointment, "Cancelled appointment is required");
        Objects.requireNonNull(entries, "Waitlist entries are required");
        Objects.requireNonNull(cancelledAt, "Cancellation time is required");

        if (!cancelledAppointment.cancelled()) {
            throw new IllegalArgumentException(
                    "Appointment must be cancelled before waitlist handling");
        }

        Optional<WaitlistEntry> next = selectNext(
                cancelledAppointment.appointmentType(), entries);

        if (next.isEmpty()) {
            return Optional.empty();
        }

        WaitlistEntry entry = next.get();

        Appointment heldAppointment;
        if (cancelledAppointment.zonedStart() != null) {
            heldAppointment = new Appointment(
                    null,
                    entry.patient(),
                    cancelledAppointment.provider(),
                    cancelledAppointment.room(),
                    cancelledAppointment.equipment(),
                    cancelledAppointment.appointmentType(),
                    cancelledAppointment.zonedStart(),
                    cancelledAppointment.bufferMinutes());
        } else {
            heldAppointment = new Appointment(
                    null,
                    entry.patient(),
                    cancelledAppointment.provider(),
                    cancelledAppointment.room(),
                    cancelledAppointment.equipment(),
                    cancelledAppointment.appointmentType(),
                    cancelledAppointment.start(),
                    cancelledAppointment.bufferMinutes());
        }

        return Optional.of(createHold(entry, heldAppointment, cancelledAt));
    }

    public WaitlistHold createHold(
            WaitlistEntry entry,
            Appointment appointment,
            LocalDateTime createdAt) {

        Objects.requireNonNull(entry, "Waitlist entry is required");
        Objects.requireNonNull(appointment, "Held appointment is required");
        Objects.requireNonNull(createdAt, "Hold creation time is required");

        WaitlistHold hold = new WaitlistHold(
                entry, appointment, createdAt, createdAt.plusMinutes(HOLD_MINUTES));

        synchronized (activeHolds) {
            activeHolds.add(hold);
        }

        return hold;
    }

    public boolean confirmHold(
            Long waitlistEntryId,
            Long patientId,
            LocalDateTime time) {

        Objects.requireNonNull(waitlistEntryId, "Waitlist entry ID is required");
        Objects.requireNonNull(patientId, "Patient ID is required");
        Objects.requireNonNull(time, "Current time is required");

        synchronized (activeHolds) {
            activeHolds.removeIf(hold -> !hold.isActiveAt(time));

            return activeHolds.stream()
                    .anyMatch(hold ->
                            sameId(hold.entry().id(), waitlistEntryId)
                                    && sameId(hold.entry().patient().id(), patientId)
                                    && hold.isActiveAt(time));
        }
    }

    public boolean confirmAndBook(
            Long waitlistEntryId,
            Long patientId,
            LocalDateTime time,
            BookingService bookingService,
            List<LeaveBlock> leaveBlocks) {

        Objects.requireNonNull(waitlistEntryId, "Waitlist entry ID is required");
        Objects.requireNonNull(patientId, "Patient ID is required");
        Objects.requireNonNull(time, "Current time is required");
        Objects.requireNonNull(bookingService, "Booking service is required");
        Objects.requireNonNull(leaveBlocks, "Leave blocks are required");

        synchronized (activeHolds) {
            activeHolds.removeIf(hold -> !hold.isActiveAt(time));

            for (WaitlistHold hold : activeHolds) {
                if (sameId(hold.entry().id(), waitlistEntryId)
                        && sameId(hold.entry().patient().id(), patientId)
                        && hold.isActiveAt(time)) {

                    if (leaveBlocks.stream().anyMatch(
                            leaveBlock -> leaveBlock.blocks(hold.appointment()))) {
                        return false;
                    }

                    if (!bookingService.tryBook(hold.appointment(), activeHolds, time)) {
                        return false;
                    }

                    activeHolds.remove(hold);
                    return true;
                }
            }
        }

        return false;
    }
    public boolean releaseHold(Long waitlistEntryId, Long patientId) {
        Objects.requireNonNull(waitlistEntryId, "Waitlist entry ID is required");
        Objects.requireNonNull(patientId, "Patient ID is required");

        synchronized (activeHolds) {
            return activeHolds.removeIf(hold ->
                    sameId(hold.entry().id(), waitlistEntryId)
                            && sameId(hold.entry().patient().id(), patientId));
        }
    }

    public List<WaitlistHold> getActiveHoldsAt(LocalDateTime time) {
        Objects.requireNonNull(time, "Current time is required");

        synchronized (activeHolds) {
            activeHolds.removeIf(hold -> !hold.isActiveAt(time));
            return List.copyOf(activeHolds);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void expireHolds() {
        LocalDateTime now = LocalDateTime.now();

        synchronized (activeHolds) {
            activeHolds.removeIf(hold -> !hold.isActiveAt(now));
        }
    }

    private boolean sameId(Long first, Long second) {
        return first != null && first.equals(second);
    }
}