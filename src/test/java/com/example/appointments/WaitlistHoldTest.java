package com.example.appointments;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WaitlistHoldTest {

    private final WaitlistService waitlistService = new WaitlistService();
    private final Patient patient = new Patient(1L, "Test Patient");
    private final Provider provider = new Provider(1L, "Dr Rao", "Cardiology");
    private final Room room = new Room(1L, "Room 1");

    private final AppointmentType followUp =
            new AppointmentType(1L, "Follow-up", 20, Set.of("Cardiology"));

    private final LocalDateTime tenAm =
            LocalDateTime.of(2026, 10, 23, 10, 0);

    private WaitlistEntry entry() {
        return new WaitlistEntry(1L, patient, followUp, 2, tenAm);
    }

    private Appointment appointmentAt(LocalDateTime start) {
        return new Appointment(
                null, patient, provider, room, null, followUp, start, 5);
    }

    @Test
    void createsHoldThatExpiresAfterFifteenMinutes() {
        WaitlistHold hold =
                waitlistService.createHold(entry(), appointmentAt(tenAm), tenAm);

        assertEquals(tenAm, hold.createdAt());
        assertEquals(tenAm.plusMinutes(15), hold.expiresAt());
        assertEquals(tenAm, hold.appointment().start());
    }

    @Test
    void holdIsActiveBeforeExpiry() {
        WaitlistHold hold =
                waitlistService.createHold(entry(), appointmentAt(tenAm), tenAm);

        assertTrue(hold.isActiveAt(tenAm.plusMinutes(14)));
    }

    @Test
    void holdIsExpiredAtExpiryTime() {
        WaitlistHold hold =
                waitlistService.createHold(entry(), appointmentAt(tenAm), tenAm);

        assertFalse(hold.isActiveAt(tenAm.plusMinutes(15)));
    }

    @Test
    void holdIsNotActiveBeforeCreationTime() {
        WaitlistHold hold =
                waitlistService.createHold(entry(), appointmentAt(tenAm), tenAm);

        assertFalse(hold.isActiveAt(tenAm.minusMinutes(1)));
    }

    @Test
    void rejectsExpiryNotAfterCreation() {
        assertThrows(IllegalArgumentException.class,
                () -> new WaitlistHold(entry(), appointmentAt(tenAm), tenAm, tenAm));
    }

    @Test
    void rejectsAppointmentTypeThatDoesNotMatchWaitlistEntry() {
        AppointmentType consultation =
                new AppointmentType(2L, "Consultation", 30, Set.of("Cardiology"));
        Appointment mismatched = new Appointment(
                null, patient, provider, room, null, consultation, tenAm, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new WaitlistHold(entry(), mismatched, tenAm, tenAm.plusMinutes(15)));
    }

    @Test
    void removesExpiredHoldsAndKeepsActiveHolds() {
        waitlistService.createHold(entry(), appointmentAt(tenAm), tenAm);
        waitlistService.createHold(
                entry(), appointmentAt(tenAm.plusMinutes(10)), tenAm.plusMinutes(10));

        var activeHolds =
                waitlistService.getActiveHoldsAt(tenAm.plusMinutes(15));

        assertEquals(1, activeHolds.size());
        assertEquals(tenAm.plusMinutes(10), activeHolds.get(0).createdAt());
    }

    @Test
    void returnsNoHoldsWhenAllHoldsHaveExpired() {
        waitlistService.createHold(entry(), appointmentAt(tenAm), tenAm);

        var activeHolds =
                waitlistService.getActiveHoldsAt(tenAm.plusMinutes(15));

        assertTrue(activeHolds.isEmpty());
    }
}