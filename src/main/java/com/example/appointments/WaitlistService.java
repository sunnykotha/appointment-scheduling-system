package com.example.appointments;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class WaitlistService {

    private static final long HOLD_MINUTES = 15;

    private final List<WaitlistHold> activeHolds = new ArrayList<>();
    private final Map<WaitlistHold, List<WaitlistEntry>> waitingCandidatesByHold = new HashMap<>();

    public Optional<WaitlistEntry> selectNext(
            AppointmentType appointmentType,
            List<WaitlistEntry> entries) {
        return selectNext(appointmentType, null, entries);
    }

    public Optional<WaitlistEntry> selectNext(
            AppointmentType appointmentType,
            LocalDateTime slotStart,
            List<WaitlistEntry> entries) {

        Objects.requireNonNull(appointmentType, "Appointment type is required");
        Objects.requireNonNull(entries, "Waitlist entries are required");

        boolean morningSlot = slotStart != null
                && slotStart.toLocalTime().isBefore(java.time.LocalTime.NOON);

        return entries.stream()
                .filter(entry -> sameId(
                        entry.appointmentType().id(), appointmentType.id()))
                .filter(entry -> !morningSlot || entry.acceptsMornings())
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
                cancelledAppointment.appointmentType(),
                cancelledAppointment.start(),
                entries);

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

        WaitlistHold hold = createHold(entry, heldAppointment, cancelledAt);
        List<WaitlistEntry> remaining = entries.stream()
                .filter(candidate -> candidate != entry
                        && !sameId(candidate.id(), entry.id()))
                .toList();
        synchronized (activeHolds) {
            waitingCandidatesByHold.put(hold, remaining);
        }
        return Optional.of(hold);
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
            expireHoldsAt(time);

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
            expireHoldsAt(time);

            for (WaitlistHold hold : activeHolds) {
                if (sameId(hold.entry().id(), waitlistEntryId)
                        && sameId(hold.entry().patient().id(), patientId)
                        && hold.isActiveAt(time)) {

                    if (leaveBlocks.stream().anyMatch(
                            leaveBlock -> leaveBlock.blocks(hold.appointment()))) {
                        return false;
                    }

                    if (!bookingService.tryBook(hold.appointment(), activeHolds, leaveBlocks, time)) {
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
            expireHoldsAt(time);
            return List.copyOf(activeHolds);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void expireHolds() {
        expireHoldsAt(LocalDateTime.now());
    }

    public void expireHoldsAt(LocalDateTime now) {
        Objects.requireNonNull(now, "Current time is required");

        synchronized (activeHolds) {
            List<WaitlistHold> expired = activeHolds.stream()
                    .filter(hold -> !hold.isActiveAt(now))
                    .toList();

            for (WaitlistHold oldHold : expired) {
                activeHolds.remove(oldHold);
                List<WaitlistEntry> candidates =
                        waitingCandidatesByHold.remove(oldHold);

                if (candidates == null || candidates.isEmpty()) {
                    continue;
                }

                Optional<WaitlistEntry> next = selectNext(
                        oldHold.appointment().appointmentType(),
                        oldHold.appointment().start(),
                        candidates);

                if (next.isEmpty()) {
                    continue;
                }

                WaitlistEntry entry = next.get();
                Appointment previous = oldHold.appointment();
                Appointment replacement;

                if (previous.zonedStart() != null) {
                    replacement = new Appointment(
                            null, entry.patient(), previous.provider(),
                            previous.room(), previous.equipment(),
                            previous.appointmentType(), previous.zonedStart(),
                            previous.bufferMinutes());
                } else {
                    replacement = new Appointment(
                            null, entry.patient(), previous.provider(),
                            previous.room(), previous.equipment(),
                            previous.appointmentType(), previous.start(),
                            previous.bufferMinutes());
                }

                WaitlistHold nextHold = createHold(entry, replacement, now);
                List<WaitlistEntry> remaining = candidates.stream()
                        .filter(candidate -> candidate != entry
                                && !sameId(candidate.id(), entry.id()))
                        .toList();
                waitingCandidatesByHold.put(nextHold, remaining);
            }
        }
    }

    private boolean sameId(Long first, Long second) {
        return first != null && first.equals(second);
    }
}
