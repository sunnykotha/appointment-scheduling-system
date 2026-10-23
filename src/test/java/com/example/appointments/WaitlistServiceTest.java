package com.example.appointments;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WaitlistServiceTest {

    private final WaitlistService waitlistService = new WaitlistService();

    private final Patient patient1 = new Patient(1L, "Patient One");
    private final Patient patient2 = new Patient(2L, "Patient Two");

    private final AppointmentType followUp =
            new AppointmentType(1L, "Follow-up", 20, Set.of("Cardiology"));

    private final AppointmentType consultation =
            new AppointmentType(2L, "Consultation", 30, Set.of("Cardiology"));

    private final LocalDateTime tenAm =
            LocalDateTime.of(2026, 10, 23, 10, 0);

    @Test
    void selectsHighestUrgencyAmongMatchingAppointmentTypes() {
        WaitlistEntry lowerUrgency =
                new WaitlistEntry(1L, patient1, followUp, 1, tenAm.minusMinutes(30));
        WaitlistEntry higherUrgency =
                new WaitlistEntry(2L, patient2, followUp, 3, tenAm);
        WaitlistEntry otherType =
                new WaitlistEntry(3L, patient1, consultation, 5, tenAm.minusMinutes(60));

        Optional<WaitlistEntry> selected = waitlistService.selectNext(
                followUp, List.of(lowerUrgency, otherType, higherUrgency));

        assertEquals(Optional.of(higherUrgency), selected);
    }

    @Test
    void selectsEarliestEntryWhenUrgencyIsEqual() {
        WaitlistEntry earlier =
                new WaitlistEntry(1L, patient1, followUp, 2, tenAm.minusMinutes(20));
        WaitlistEntry later =
                new WaitlistEntry(2L, patient2, followUp, 2, tenAm);

        assertEquals(
                Optional.of(earlier),
                waitlistService.selectNext(followUp, List.of(later, earlier)));
    }

    @Test
    void returnsEmptyWhenNoEntryMatchesAppointmentType() {
        WaitlistEntry entry =
                new WaitlistEntry(1L, patient1, consultation, 2, tenAm);

        assertTrue(waitlistService.selectNext(followUp, List.of(entry)).isEmpty());
    }

    @Test
    void returnsEmptyForAnEmptyWaitlist() {
        assertTrue(waitlistService.selectNext(followUp, List.of()).isEmpty());
    }

    @Test
    void skipsEntriesThatDoNotAcceptMorningSlots() {
        WaitlistEntry morningUnavailable =
                new WaitlistEntry(1L, patient1, followUp, 5, tenAm, false);
        WaitlistEntry morningAvailable =
                new WaitlistEntry(2L, patient2, followUp, 2, tenAm, true);

        assertEquals(
                Optional.of(morningAvailable),
                waitlistService.selectNext(
                        followUp, tenAm, List.of(morningUnavailable, morningAvailable)));
    }
    @Test
    void cancellationCreatesHoldForHighestPriorityMatchingEntry() {
        var start = java.time.LocalDateTime.of(2026, 10, 23, 10, 0);
        var cancelledAt = start.minusHours(1);
        var patient = new Patient(1L, "Original Patient");
        var provider = new Provider(1L, "Dr Rao", "Cardiology");
        var room = new Room(1L, "Room 1");
        var equipment = new Equipment(1L, "ECG");
        var type = new AppointmentType(
                1L, "Follow-up", 20, java.util.Set.of("Cardiology"));

        var cancelled = new Appointment(
                1L, patient, provider, room, equipment, type, start, 5).cancel();

        var lowerPriority = new WaitlistEntry(
                1L, new Patient(2L, "Lower Priority"), type, 1,
                cancelledAt.minusMinutes(10));
        var higherPriority = new WaitlistEntry(
                2L, new Patient(3L, "Higher Priority"), type, 5,
                cancelledAt);

        var service = new WaitlistService();
        var result = service.handleCancellation(
                cancelled, java.util.List.of(lowerPriority, higherPriority), cancelledAt);

        org.junit.jupiter.api.Assertions.assertTrue(result.isPresent());
        var hold = result.orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals(higherPriority, hold.entry());
        org.junit.jupiter.api.Assertions.assertEquals(
                higherPriority.patient(), hold.appointment().patient());
        org.junit.jupiter.api.Assertions.assertEquals(start, hold.appointment().start());
        org.junit.jupiter.api.Assertions.assertEquals(provider, hold.appointment().provider());
        org.junit.jupiter.api.Assertions.assertEquals(room, hold.appointment().room());
        org.junit.jupiter.api.Assertions.assertEquals(equipment, hold.appointment().equipment());
        org.junit.jupiter.api.Assertions.assertEquals(5, hold.appointment().bufferMinutes());
        org.junit.jupiter.api.Assertions.assertEquals(
                cancelledAt.plusMinutes(15), hold.expiresAt());
    }

    @Test
    void cancellationPreservesZonedStartInWaitlistHold() {
        ZoneId zone = ZoneId.of("America/New_York");
        ZonedDateTime zonedStart = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 3, 8, 1, 50),
                zone,
                ZoneOffset.ofHours(-5));

        Provider provider = new Provider(1L, "Dr Rao", "Cardiology");
        Room room = new Room(1L, "Room 1");

        Appointment cancelled = new Appointment(
                1L, patient1, provider, room, null, followUp,
                zonedStart, 5).cancel();

        WaitlistEntry entry = new WaitlistEntry(
                20L, patient2, followUp, 3, tenAm);

        WaitlistService service = new WaitlistService();
        Optional<WaitlistHold> result = service.handleCancellation(
                cancelled, List.of(entry), tenAm);

        assertTrue(result.isPresent());
        Appointment heldAppointment = result.orElseThrow().appointment();

        assertEquals(zonedStart, heldAppointment.zonedStart());
        assertEquals(zonedStart.toLocalDateTime(), heldAppointment.start());
        assertEquals(20, heldAppointment.getAppointmentSlot().durationMinutes());
        assertEquals(25, heldAppointment.getReservedSlot().durationMinutes());
        assertEquals(patient2, heldAppointment.patient());
    }

    @Test
    void cancellationWithoutMatchingWaitlistEntryCreatesNoHold() {
        var start = java.time.LocalDateTime.of(2026, 10, 23, 10, 0);
        var patient = new Patient(1L, "Original Patient");
        var provider = new Provider(1L, "Dr Rao", "Cardiology");
        var room = new Room(1L, "Room 1");
        var followUp = new AppointmentType(
                1L, "Follow-up", 20, java.util.Set.of("Cardiology"));
        var consultation = new AppointmentType(
                2L, "Consultation", 30, java.util.Set.of("Cardiology"));

        var cancelled = new Appointment(
                1L, patient, provider, room, null,
                followUp, start, 5).cancel();

        var unrelatedEntry = new WaitlistEntry(
                1L, new Patient(2L, "Other Patient"), consultation, 5,
                start.minusHours(2));

        var service = new WaitlistService();
        var result = service.handleCancellation(
                cancelled, java.util.List.of(unrelatedEntry), start);

        org.junit.jupiter.api.Assertions.assertTrue(result.isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                service.getActiveHoldsAt(start).isEmpty());
    }

    @Test
    void assignedPatientCanConfirmActiveHold() {
        WaitlistEntry entry =
                new WaitlistEntry(10L, patient1, followUp, 2, tenAm);

        Appointment appointment = new Appointment(
                null, patient1,
                new Provider(1L, "Dr Rao", "Cardiology"),
                new Room(1L, "Room 1"), null,
                followUp, tenAm, 5);

        waitlistService.createHold(entry, appointment, tenAm);

        assertTrue(waitlistService.confirmHold(
                10L, patient1.id(), tenAm.plusMinutes(5)));
    }

    @Test
    void differentPatientCannotConfirmHold() {
        WaitlistEntry entry =
                new WaitlistEntry(10L, patient1, followUp, 2, tenAm);

        Appointment appointment = new Appointment(
                null, patient1,
                new Provider(1L, "Dr Rao", "Cardiology"),
                new Room(1L, "Room 1"), null,
                followUp, tenAm, 5);

        waitlistService.createHold(entry, appointment, tenAm);

        assertFalse(waitlistService.confirmHold(
                10L, patient2.id(), tenAm.plusMinutes(5)));
    }

    @Test
    void expiredHoldCannotBeConfirmed() {
        WaitlistEntry entry =
                new WaitlistEntry(10L, patient1, followUp, 2, tenAm);

        Appointment appointment = new Appointment(
                null, patient1,
                new Provider(1L, "Dr Rao", "Cardiology"),
                new Room(1L, "Room 1"), null,
                followUp, tenAm, 5);

        waitlistService.createHold(entry, appointment, tenAm);

        assertFalse(waitlistService.confirmHold(
                10L, patient1.id(), tenAm.plusMinutes(15)));
    }

    @Test
    void confirmsHoldAndBooksAppointment() {
        WaitlistEntry entry =
                new WaitlistEntry(10L, patient1, followUp, 2, tenAm);

        Appointment appointment = new Appointment(
                null, patient1, new Provider(1L, "Dr Rao", "Cardiology"),
                new Room(1L, "Room 1"), null, followUp, tenAm, 5);

        waitlistService.createHold(entry, appointment, tenAm);
        BookingService bookingService = new BookingService();

        assertTrue(waitlistService.confirmAndBook(
                10L, patient1.id(), tenAm.plusMinutes(5), bookingService, List.of()));
        assertEquals(1, bookingService.getBookedAppointments().size());
        assertTrue(waitlistService.getActiveHoldsAt(tenAm.plusMinutes(5)).isEmpty());
    }

    @Test
    void conflictingBookingDoesNotConsumeWaitlistHold() {
        WaitlistEntry entry =
                new WaitlistEntry(10L, patient1, followUp, 2, tenAm);

        Appointment heldAppointment = new Appointment(
                null, patient1, new Provider(1L, "Dr Rao", "Cardiology"), new Room(1L, "Room 1"), null, followUp, tenAm, 5);

        waitlistService.createHold(entry, heldAppointment, tenAm);
        BookingService bookingService = new BookingService();

        Appointment conflict = new Appointment(
                null, patient2, new Provider(1L, "Dr Rao", "Cardiology"), new Room(2L, "Room 2"),
                null, followUp, tenAm, 0);

        assertTrue(bookingService.tryBook(conflict));

        assertFalse(waitlistService.confirmAndBook(
                10L, patient1.id(), tenAm.plusMinutes(5), bookingService, List.of()));
        assertEquals(1, bookingService.getBookedAppointments().size());
        assertEquals(1, waitlistService.getActiveHoldsAt(tenAm.plusMinutes(5)).size());
    }

    @Test
    void confirmationRejectsBookingThatConflictsWithAnotherActiveHold() {
        WaitlistEntry firstEntry =
                new WaitlistEntry(101L, patient1, followUp, 2, tenAm);
        Appointment firstAppointment = new Appointment(
                null, patient1, new Provider(1L, "Dr Rao", "Cardiology"),
                new Room(1L, "Room 1"), null, followUp, tenAm, 5);
        waitlistService.createHold(firstEntry, firstAppointment, tenAm);

        Patient otherPatient = new Patient(3L, "Patient Three");
        WaitlistEntry secondEntry =
                new WaitlistEntry(102L, otherPatient, followUp, 1, tenAm);
        Appointment secondAppointment = new Appointment(
                null, otherPatient, new Provider(1L, "Dr Rao", "Cardiology"),
                new Room(2L, "Room 2"), null, followUp, tenAm, 5);
        waitlistService.createHold(secondEntry, secondAppointment, tenAm);

        BookingService bookingService = new BookingService();

        assertFalse(waitlistService.confirmAndBook(
                102L, otherPatient.id(), tenAm.plusMinutes(1),
                bookingService, List.of()));

        assertTrue(bookingService.getBookedAppointments().isEmpty());
        assertEquals(2, waitlistService.getActiveHoldsAt(
                tenAm.plusMinutes(1)).size());
    }
    @Test
    void providerLeavePreventsWaitlistConfirmationAndKeepsHold() {
        WaitlistEntry entry =
                new WaitlistEntry(10L, patient1, followUp, 2, tenAm);

        Provider leaveProvider = new Provider(1L, "Dr Rao", "Cardiology");
        Appointment heldAppointment = new Appointment(
                null, patient1, leaveProvider, new Room(1L, "Room 1"),
                null, followUp, tenAm, 5);

        waitlistService.createHold(entry, heldAppointment, tenAm);

        LeaveBlock leaveBlock = new LeaveBlock(
                1L, leaveProvider, tenAm.minusMinutes(5),
                tenAm.plusMinutes(30), "Provider leave");

        BookingService bookingService = new BookingService();

        assertFalse(waitlistService.confirmAndBook(
                10L, patient1.id(), tenAm.plusMinutes(5),
                bookingService, List.of(leaveBlock)));

        assertTrue(bookingService.getBookedAppointments().isEmpty());
        assertEquals(1, waitlistService.getActiveHoldsAt(
                tenAm.plusMinutes(5)).size());
    }
    @Test
    void expiredHoldIsOfferedToNextEligibleWaitlistEntry() {
        LocalDateTime cancelledAt = LocalDateTime.now().minusMinutes(30);
        Provider provider = new Provider(1L, "Dr Rao", "Cardiology");
        Room room = new Room(1L, "Room 1");

        Appointment cancelled = new Appointment(
                50L, patient1, provider, room, null, followUp, tenAm, 5).cancel();

        WaitlistEntry first = new WaitlistEntry(
                31L, patient1, followUp, 5, cancelledAt);
        WaitlistEntry second = new WaitlistEntry(
                32L, patient2, followUp, 3, cancelledAt.plusMinutes(1));
        WaitlistEntry third = new WaitlistEntry(
                33L, new Patient(3L, "Patient Three"), followUp,
                1, cancelledAt.plusMinutes(2));

        WaitlistService service = new WaitlistService();
        WaitlistHold initial = service.handleCancellation(
                cancelled, List.of(first, second, third), cancelledAt).orElseThrow();

        assertEquals(first, initial.entry());

        service.expireHolds();

        List<WaitlistHold> active = service.getActiveHoldsAt(LocalDateTime.now());
        assertEquals(1, active.size());
        assertEquals(second, active.get(0).entry());
    }
void assignedPatientCanReleaseHold() {
        WaitlistEntry entry =
                new WaitlistEntry(10L, patient1, followUp, 2, tenAm);

        Appointment appointment = new Appointment(
                null, patient1,
                new Provider(1L, "Dr Rao", "Cardiology"),
                new Room(1L, "Room 1"), null,
                followUp, tenAm, 5);

        waitlistService.createHold(entry, appointment, tenAm);

        assertTrue(waitlistService.releaseHold(10L, patient1.id()));
        assertTrue(waitlistService.getActiveHoldsAt(tenAm).isEmpty());
    }
}