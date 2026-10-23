package com.example.appointments;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

public class BookingService {

    private final List<Appointment> bookedAppointments = new ArrayList<>();
    private final Map<Patient, Integer> lateCancellationStrikes = new HashMap<>();

    public boolean canBook(Appointment candidate, List<Appointment> existing) {
        Objects.requireNonNull(candidate, "Candidate appointment is required");
        Objects.requireNonNull(existing, "Existing appointments are required");

        for (Appointment appointment : existing) {
            if (!appointment.cancelled() && conflictsWith(candidate, appointment)) {
                return false;
            }
        }

        return true;
    }

    public synchronized boolean tryBook(Appointment candidate) {
        Objects.requireNonNull(candidate, "Candidate appointment is required");

        if (!canBook(candidate, bookedAppointments)) {
            return false;
        }

        bookedAppointments.add(candidate);
        return true;
    }

    public synchronized boolean tryBook(
            Appointment candidate,
            List<WaitlistHold> holds,
            LocalDateTime time) {

        Objects.requireNonNull(candidate, "Candidate appointment is required");

        if (!canBook(candidate, bookedAppointments, holds, time)) {
            return false;
        }

        bookedAppointments.add(candidate);
        return true;
    }
    public synchronized boolean tryBook(
            Appointment candidate,
            List<WaitlistHold> holds,
            List<LeaveBlock> leaveBlocks,
            LocalDateTime time) {

        Objects.requireNonNull(candidate, "Candidate appointment is required");
        Objects.requireNonNull(holds, "Waitlist holds are required");
        Objects.requireNonNull(leaveBlocks, "Leave blocks are required");
        Objects.requireNonNull(time, "Current time is required");

        if (!canBook(candidate, bookedAppointments, holds, time)) {
            return false;
        }

        for (LeaveBlock leaveBlock : leaveBlocks) {
            if (leaveBlock.blocks(candidate)) {
                return false;
            }
        }

        bookedAppointments.add(candidate);
        return true;
    }
    public synchronized List<Appointment> getBookedAppointments() {
        return List.copyOf(bookedAppointments);
    }

    public synchronized boolean cancelBooking(Long appointmentId) {
        Objects.requireNonNull(appointmentId, "Appointment ID is required");

        for (int i = 0; i < bookedAppointments.size(); i++) {
            Appointment appointment = bookedAppointments.get(i);

            if (appointmentId.equals(appointment.id())
                    && !appointment.cancelled()) {
                bookedAppointments.set(i, appointment.cancel());
                return true;
            }
        }

        return false;
    }

    public synchronized boolean cancelBooking(
            Long appointmentId, LocalDateTime cancelledAt) {
        Objects.requireNonNull(appointmentId, "Appointment ID is required");
        Objects.requireNonNull(cancelledAt, "Cancellation time is required");

        Appointment appointment = bookedAppointments.stream()
                .filter(item -> appointmentId.equals(item.id()))
                .filter(item -> !item.cancelled())
                .findFirst()
                .orElse(null);

        if (appointment == null) {
            return false;
        }

        if (!cancelBooking(appointmentId)) {
            return false;
        }

        if (cancelledAt.isBefore(appointment.start())
                && java.time.Duration.between(cancelledAt, appointment.start())
                        .compareTo(java.time.Duration.ofHours(24)) < 0) {
            lateCancellationStrikes.merge(appointment.patient(), 1, Integer::sum);
        }

        return true;
    }

    public synchronized int getLateCancellationStrikeCount(Patient patient) {
        Objects.requireNonNull(patient, "Patient is required");
        return lateCancellationStrikes.getOrDefault(patient, 0);
    }
    public synchronized java.util.Optional<WaitlistHold> cancelAndHandleWaitlist(
            Long appointmentId,
            WaitlistService waitlistService,
            List<WaitlistEntry> entries,
            LocalDateTime cancelledAt) {

        Objects.requireNonNull(waitlistService, "Waitlist service is required");
        Objects.requireNonNull(entries, "Waitlist entries are required");
        Objects.requireNonNull(cancelledAt, "Cancellation time is required");

        boolean cancelled = cancelBooking(appointmentId, cancelledAt);
        if (!cancelled) {
            return java.util.Optional.empty();
        }

        Appointment cancelledAppointment = bookedAppointments.stream()
                .filter(appointment -> appointmentId.equals(appointment.id()))
                .filter(Appointment::cancelled)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cancelled appointment could not be found"));

        return waitlistService.handleCancellation(
                cancelledAppointment, entries, cancelledAt);
    }
    public boolean canBook(
            Appointment candidate,
            List<Appointment> existing,
            List<WaitlistHold> holds,
            LocalDateTime time) {

        Objects.requireNonNull(candidate, "Candidate appointment is required");
        Objects.requireNonNull(existing, "Existing appointments are required");
        Objects.requireNonNull(holds, "Waitlist holds are required");
        Objects.requireNonNull(time, "Current time is required");

        if (!canBook(candidate, existing)) {
            return false;
        }

        for (WaitlistHold hold : holds) {
            if (!hold.isActiveAt(time)) {
                continue;
            }

            if (sameId(candidate.patient().id(), hold.entry().patient().id())) {
                continue;
            }

            if (conflictsWith(candidate, hold.appointment())) {
                return false;
            }
        }

        return true;
    }

    public boolean canBook(
            Appointment candidate,
            List<Appointment> existing,
            List<WaitlistHold> holds,
            List<LeaveBlock> leaveBlocks,
            LocalDateTime time) {

        Objects.requireNonNull(leaveBlocks, "Leave blocks are required");

        if (!canBook(candidate, existing, holds, time)) {
            return false;
        }

        for (LeaveBlock leaveBlock : leaveBlocks) {
            if (leaveBlock.blocks(candidate)) {
                return false;
            }
        }

        return true;
    }

    private boolean conflictsWith(Appointment first, Appointment second) {
        return first.getReservedSlot().overlaps(second.getReservedSlot())
                && (sameId(first.patient().id(), second.patient().id())
                || sharesResource(first, second));
    }

    private boolean sharesResource(Appointment first, Appointment second) {
        return sameId(first.provider().id(), second.provider().id())
                || sameId(first.room().id(), second.room().id())
                || (first.equipment() != null
                    && second.equipment() != null
                    && sameId(first.equipment().id(), second.equipment().id()));
    }

    private boolean sameId(Long first, Long second) {
        return first != null && first.equals(second);
    }
}