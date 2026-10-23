package com.example.appointments;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BookingServiceTest {

    private final BookingService bookingService = new BookingService();

    private final Patient patient = new Patient(1L, "Test Patient");
    private final Provider provider = new Provider(1L, "Dr Rao", "Cardiology");
    private final Room room = new Room(1L, "Room 1");
    private final Equipment equipment = new Equipment(1L, "ECG");

    private final AppointmentType followUp =
            new AppointmentType(1L, "Follow-up", 20, Set.of("Cardiology"));

    private final LocalDateTime tenAm =
            LocalDateTime.of(2026, 10, 23, 10, 0);

    private Appointment appointmentAt(LocalDateTime start) {
        return new Appointment(
                null, patient, provider, room, equipment,
                followUp, start, 5);
    }

    @Test
    void rejectsBookingDuringPreviousAppointmentBuffer() {
        Appointment existing = appointmentAt(tenAm);
        Appointment candidate = appointmentAt(tenAm.plusMinutes(20));

        assertFalse(bookingService.canBook(candidate, List.of(existing)));
    }

    @Test
    void acceptsBookingWhenPreviousBufferHasEnded() {
        Appointment existing = appointmentAt(tenAm);
        Appointment candidate = appointmentAt(tenAm.plusMinutes(25));

        assertTrue(bookingService.canBook(candidate, List.of(existing)));
    }

    @Test
    void rejectsOverlappingAppointmentWithSameProvider() {
        Appointment existing = appointmentAt(tenAm);
        Appointment candidate = new Appointment(
                null, patient, provider, new Room(2L, "Room 2"), null,
                followUp, tenAm.plusMinutes(10), 0);

        assertFalse(bookingService.canBook(candidate, List.of(existing)));
    }

    @Test
    void rejectsOverlappingAppointmentWithSameRoom() {
        Appointment existing = appointmentAt(tenAm);
        Appointment candidate = new Appointment(
                null, patient, new Provider(2L, "Dr Kumar", "Cardiology"),
                room, null, followUp, tenAm.plusMinutes(10), 0);

        assertFalse(bookingService.canBook(candidate, List.of(existing)));
    }

    @Test
    void rejectsOverlappingAppointmentWithSameEquipment() {
        Appointment existing = appointmentAt(tenAm);
        Appointment candidate = new Appointment(
                null, patient, new Provider(2L, "Dr Kumar", "Cardiology"),
                new Room(2L, "Room 2"), equipment,
                followUp, tenAm.plusMinutes(10), 0);

        assertFalse(bookingService.canBook(candidate, List.of(existing)));
    }

    @Test
    void rejectsOverlappingAppointmentForSamePatientWithDifferentResources() {
        Appointment existing = appointmentAt(tenAm);
        Appointment candidate = new Appointment(
                null, patient, new Provider(2L, "Dr Kumar", "Cardiology"),
                new Room(2L, "Room 2"), null,
                followUp, tenAm.plusMinutes(10), 0);

        assertFalse(bookingService.canBook(candidate, List.of(existing)));
    }

    @Test
    void acceptsAppointmentWithDifferentPatientAndDifferentResources() {
        Appointment existing = appointmentAt(tenAm);
        Appointment candidate = new Appointment(
                null, new Patient(2L, "Another Patient"),
                new Provider(2L, "Dr Kumar", "Cardiology"),
                new Room(2L, "Room 2"), null,
                followUp, tenAm.plusMinutes(10), 0);

        assertTrue(bookingService.canBook(candidate, List.of(existing)));
    }

    @Test
    void rejectsBookingOnRegisteredHoliday() {
        bookingService.applyHolidayBlock(new HolidayBlock(tenAm.toLocalDate(), null));
        assertFalse(bookingService.tryBook(appointmentAt(tenAm)));
    }

    @Test
    void allowsBookingOutsideRegisteredHolidayDate() {
        bookingService.applyHolidayBlock(new HolidayBlock(tenAm.toLocalDate(), null));
        assertTrue(bookingService.tryBook(appointmentAt(tenAm.plusDays(1))));
    }
    @Test
    void cancelledAppointmentDoesNotBlockItsPreviousTimeSlot() {
        Appointment existing = appointmentAt(tenAm);
        Appointment cancelled = existing.cancel();
        Appointment candidate = appointmentAt(tenAm);

        assertTrue(cancelled.cancelled());
        assertFalse(existing.cancelled());
        assertTrue(bookingService.canBook(candidate, List.of(cancelled)));
    }

    @Test
    void cancellingAppointmentPreservesItsDetails() {
        Appointment existing = appointmentAt(tenAm);
        Appointment cancelled = existing.cancel();

        assertEquals(existing.id(), cancelled.id());
        assertEquals(existing.patient(), cancelled.patient());
        assertEquals(existing.start(), cancelled.start());
        assertEquals(existing.getReservedSlot(), cancelled.getReservedSlot());
    }

    @Test
    void cancellationLessThan24HoursBeforeAppointmentRecordsStrike() {
        Appointment appointment = new Appointment(
                501L, patient, provider, room, equipment,
                followUp, tenAm, 5);
        assertTrue(bookingService.tryBook(appointment));

        assertTrue(bookingService.cancelBooking(501L, tenAm.minusHours(23)));
        assertEquals(1, bookingService.getLateCancellationStrikeCount(patient));
    }

    @Test
    void cancellationExactly24HoursBeforeAppointmentDoesNotRecordStrike() {
        Appointment appointment = new Appointment(
                502L, patient, provider, room, equipment,
                followUp, tenAm, 5);
        assertTrue(bookingService.tryBook(appointment));

        assertTrue(bookingService.cancelBooking(502L, tenAm.minusHours(24)));
        assertEquals(0, bookingService.getLateCancellationStrikeCount(patient));
    }

    @Test
    void alreadyCancelledAppointmentCannotRecordAnotherStrike() {
        Appointment appointment = new Appointment(
                503L, patient, provider, room, equipment,
                followUp, tenAm, 5);
        assertTrue(bookingService.tryBook(appointment));

        assertTrue(bookingService.cancelBooking(503L, tenAm.minusHours(23)));
        assertFalse(bookingService.cancelBooking(503L, tenAm.minusHours(22)));
        assertEquals(1, bookingService.getLateCancellationStrikeCount(patient));
    }
    @Test
    void simultaneousConflictingBookingsAllowOnlyOneSuccess() throws Exception {
        var service = new BookingService();
        var first = appointmentAt(tenAm);
        var second = new Appointment(
                null, new Patient(2L, "Another Patient"),
                provider, new Room(2L, "Room 2"), null,
                followUp, tenAm, 5);

        var ready = new CountDownLatch(2);
        var startTogether = new CountDownLatch(1);
        var successes = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstAttempt = executor.submit(() -> {
                ready.countDown();
                startTogether.await();
                if (service.tryBook(first)) {
                    successes.incrementAndGet();
                }
                return null;
            });

            var secondAttempt = executor.submit(() -> {
                ready.countDown();
                startTogether.await();
                if (service.tryBook(second)) {
                    successes.incrementAndGet();
                }
                return null;
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            startTogether.countDown();

            firstAttempt.get(5, TimeUnit.SECONDS);
            secondAttempt.get(5, TimeUnit.SECONDS);
        }

        assertEquals(1, successes.get());
        assertEquals(1, service.getBookedAppointments().size());
    }
    @Test
    void rejectsNegativeBuffer() {
        assertThrows(IllegalArgumentException.class,
                () -> new Appointment(
                        null, patient, provider, room, null,
                        followUp, tenAm, -1));
    }

    @Test
    void rejectsAppointmentForUnsupportedSpecialty() {
        AppointmentType dermatologyOnly =
                new AppointmentType(2L, "Skin consultation", 30,
                        Set.of("Dermatology"));

        assertThrows(IllegalArgumentException.class,
                () -> new Appointment(
                        null, patient, provider, room, null,
                        dermatologyOnly, tenAm, 0));
    }

    @Test
    void activeWaitlistHoldBlocksAnotherPatientsConflictingBooking() {
        Patient waitingPatient = new Patient(2L, "Waiting Patient");
        Appointment heldAppointment = new Appointment(
                null, waitingPatient, provider, room, equipment,
                followUp, tenAm, 5);

        WaitlistEntry entry = new WaitlistEntry(
                1L, waitingPatient, followUp, 1, tenAm.minusMinutes(30));

        WaitlistService waitlistService = new WaitlistService();
        WaitlistHold hold = waitlistService.createHold(
                entry, heldAppointment, tenAm);

        Appointment candidate = new Appointment(
                null, new Patient(3L, "Another Patient"),
                provider, new Room(3L, "Room 3"), null,
                followUp, tenAm, 0);

        assertFalse(bookingService.canBook(
                candidate, List.of(), List.of(hold), tenAm.plusMinutes(1)));
    }

    @Test
    void expiredWaitlistHoldDoesNotBlockBooking() {
        Patient waitingPatient = new Patient(2L, "Waiting Patient");
        Appointment heldAppointment = new Appointment(
                null, waitingPatient, provider, room, equipment,
                followUp, tenAm, 5);

        WaitlistEntry entry = new WaitlistEntry(
                1L, waitingPatient, followUp, 1, tenAm.minusMinutes(30));

        WaitlistService waitlistService = new WaitlistService();
        WaitlistHold hold = waitlistService.createHold(
                entry, heldAppointment, tenAm);

        Appointment candidate = new Appointment(
                null, new Patient(3L, "Another Patient"),
                provider, new Room(3L, "Room 3"), null,
                followUp, tenAm, 0);

        assertTrue(bookingService.canBook(
                candidate, List.of(), List.of(hold), tenAm.plusMinutes(15)));
    }

    @Test
    void patientOwningWaitlistHoldCanConfirmHeldSlot() {
        Patient waitingPatient = new Patient(2L, "Waiting Patient");
        Appointment heldAppointment = new Appointment(
                null, waitingPatient, provider, room, equipment,
                followUp, tenAm, 5);

        WaitlistEntry entry = new WaitlistEntry(
                1L, waitingPatient, followUp, 1, tenAm.minusMinutes(30));

        WaitlistService waitlistService = new WaitlistService();
        WaitlistHold hold = waitlistService.createHold(
                entry, heldAppointment, tenAm);

        assertTrue(bookingService.canBook(
                heldAppointment, List.of(), List.of(hold), tenAm.plusMinutes(1)));
    }

    @Test
    void rejectsBookingDuringProviderLeave() {
        var provider = new Provider(1L, "Dr Rao", "Cardiology");
        var patient = new Patient(1L, "Patient One");
        var room = new Room(1L, "Room 1");
        var type = new AppointmentType(
                1L, "Follow-up", 20, java.util.Set.of("Cardiology"));
        var start = java.time.LocalDateTime.of(2026, 10, 23, 10, 0);

        var candidate = new Appointment(
                1L, patient, provider, room, null, type, start, 5);
        var leave = new LeaveBlock(
                1L, provider, start.plusMinutes(10),
                start.plusHours(1), "Holiday");

        var service = new BookingService();

        org.junit.jupiter.api.Assertions.assertFalse(
                service.canBook(
                        candidate,
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(leave),
                        start.minusHours(1)));
    }

    @Test
    void acceptsBookingOutsideProviderLeave() {
        var provider = new Provider(1L, "Dr Rao", "Cardiology");
        var patient = new Patient(1L, "Patient One");
        var room = new Room(1L, "Room 1");
        var type = new AppointmentType(
                1L, "Follow-up", 20, java.util.Set.of("Cardiology"));
        var leaveStart = java.time.LocalDateTime.of(2026, 10, 23, 10, 0);

        var candidate = new Appointment(
                1L, patient, provider, room, null, type,
                leaveStart.plusHours(2), 5);
        var leave = new LeaveBlock(
                1L, provider, leaveStart, leaveStart.plusHours(1), "Holiday");

        var service = new BookingService();

        org.junit.jupiter.api.Assertions.assertTrue(
                service.canBook(
                        candidate,
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(leave),
                        leaveStart.minusHours(1)));
    }
    @Test
    void cancellingBookingCreatesHoldForHighestPriorityWaitlistEntry() {
        var bookingService = new BookingService();
        var waitlistService = new WaitlistService();
        var start = java.time.LocalDateTime.of(2026, 10, 23, 10, 0);

        var bookedAppointment = new Appointment(
                101L, patient, provider, room, equipment, followUp, start, 5);

        assertTrue(bookingService.tryBook(bookedAppointment));

        var waitingPatient = new Patient(2L, "Waiting Patient");
        var lowerPriority = new WaitlistEntry(
                201L, waitingPatient, followUp, 1, start.minusMinutes(20));
        var higherPriority = new WaitlistEntry(
                202L, new Patient(3L, "Higher Priority Patient"),
                followUp, 5, start.minusMinutes(10));

        var cancelledAt = start.minusHours(1);

        var hold = bookingService.cancelAndHandleWaitlist(
                101L,
                waitlistService,
                java.util.List.of(lowerPriority, higherPriority),
                cancelledAt);

        assertTrue(hold.isPresent());
        assertEquals(202L, hold.get().entry().id());
        org.junit.jupiter.api.Assertions.assertNull(
                hold.get().appointment().id());
        assertEquals(start, hold.get().appointment().start());
        assertEquals(cancelledAt.plusMinutes(15), hold.get().expiresAt());
        assertTrue(bookingService.getBookedAppointments().get(0).cancelled());
    }
    @Test
    void holdAwareBookingRejectsAnotherPatientsHeldSlot() {
        var service = new BookingService();
        var waitingPatient = new Patient(2L, "Waiting Patient");
        var entry = new WaitlistEntry(
                301L, waitingPatient, followUp, 1, tenAm.minusMinutes(30));
        var heldAppointment = new Appointment(
                null, waitingPatient, provider, room, equipment,
                followUp, tenAm, 5);
        var hold = new WaitlistService().createHold(entry, heldAppointment, tenAm);

        var otherPatientAppointment = new Appointment(
                null, new Patient(3L, "Another Patient"), provider, room,
                equipment, followUp, tenAm, 0);

        assertFalse(service.tryBook(
                otherPatientAppointment,
                List.of(hold),
                tenAm.plusMinutes(1)));

        assertTrue(service.getBookedAppointments().isEmpty());
    }
    @Test
    void applyingProviderLeaveFlagsOnlyAffectedStoredAppointments() {
        var service = new BookingService();

        var affected = new Appointment(
                601L, patient, provider, room, equipment,
                followUp, tenAm, 5);

        var otherProvider = new Provider(2L, "Dr Kumar", "Cardiology");
        var unaffected = new Appointment(
                602L, new Patient(2L, "Another Patient"),
                otherProvider, new Room(2L, "Room 2"), null,
                followUp, tenAm, 0);

        assertTrue(service.tryBook(affected));
        assertTrue(service.tryBook(unaffected));

        var leave = new LeaveBlock(
                1L, provider, tenAm.plusMinutes(10),
                tenAm.plusHours(1), "Provider leave");

        var flagged = service.applyLeaveBlock(leave);
        var stored = service.getBookedAppointments();

        assertEquals(1, flagged.size());
        assertEquals(601L, flagged.get(0).id());
        assertTrue(stored.stream()
                .filter(a -> a.id().equals(601L))
                .findFirst().orElseThrow().reschedulingRequired());
        assertFalse(stored.stream()
                .filter(a -> a.id().equals(602L))
                .findFirst().orElseThrow().reschedulingRequired());
    }

    @Test
    void leaveAwareBookingRejectsAppointmentDuringProviderLeave() {
        var service = new BookingService();
        var candidate = appointmentAt(tenAm);

        var leave = new LeaveBlock(
                701L, provider, tenAm.plusMinutes(10),
                tenAm.plusHours(1), "Provider leave");

        assertFalse(service.tryBook(
                candidate,
                List.of(),
                List.of(leave),
                tenAm.minusHours(1)));

        assertTrue(service.getBookedAppointments().isEmpty());
    }

    @Test
    void normalBookingRejectsAppointmentAfterProviderLeaveIsRegistered() {
        var service = new BookingService();
        var candidate = appointmentAt(tenAm);

        var leave = new LeaveBlock(
                702L, provider, tenAm.plusMinutes(10),
                tenAm.plusHours(1), "Provider leave");

        service.applyLeaveBlock(leave);

        assertFalse(service.tryBook(candidate));
        assertTrue(service.getBookedAppointments().isEmpty());
    }
}