package com.example.appointments;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingEntitiesTest {

    @Test
    void createsPatientProviderRoomAndEquipment() {
        assertDoesNotThrow(() -> new Patient(1L, "Test Patient"));
        assertDoesNotThrow(() -> new Provider(1L, "Dr Rao", "Cardiology"));
        assertDoesNotThrow(() -> new Room(1L, "Room 1"));
        assertDoesNotThrow(() -> new Equipment(1L, "ECG"));
    }

    @Test
    void appointmentTypeUsesConfiguredDurationAndSpecialty() {
        AppointmentType type = new AppointmentType(
                1L, "Follow-up", 20, Set.of("Cardiology"));

        assertEquals(20, type.durationMinutes());
        assertTrue(type.allowsSpecialty("Cardiology"));
        assertFalse(type.allowsSpecialty("Dermatology"));
    }

    @Test
    void rejectsNonPositiveAppointmentDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new AppointmentType(1L, "Follow-up", 0, Set.of()));
    }

    @Test
    void calculatesTimeSlotDuration() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 23, 10, 0);
        LocalDateTime end = start.plusMinutes(20);

        assertEquals(20, new TimeSlot(start, end).durationMinutes());
    }

    @Test
    void detectsOverlappingTimeSlots() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 23, 10, 0);

        TimeSlot first = new TimeSlot(start, start.plusMinutes(20));
        TimeSlot overlapping = new TimeSlot(
                start.plusMinutes(15), start.plusMinutes(35));
        TimeSlot adjacent = new TimeSlot(
                start.plusMinutes(20), start.plusMinutes(40));

        assertTrue(first.overlaps(overlapping));
        assertFalse(first.overlaps(adjacent));
    }

    @Test
    void rejectsInvalidTimeSlot() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 23, 10, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new TimeSlot(start, start));
    }

    @Test
    void calculatesElapsedDurationAcrossSpringDstTransition() {
        ZoneId zone = ZoneId.of("America/New_York");
        ZonedDateTime start = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 3, 8, 1, 50),
                zone, ZoneOffset.ofHours(-5));
        ZonedDateTime end = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 3, 8, 3, 10),
                zone, ZoneOffset.ofHours(-4));

        TimeSlot slot = new TimeSlot(start, end);

        assertEquals(20, slot.durationMinutes());
    }

    @Test
    void comparesZonedSlotWithLegacySlotUsingZonedRegion() {
        ZoneId zone = ZoneId.of("America/New_York");
        ZonedDateTime zonedStart = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 11, 1, 1, 30),
                zone,
                ZoneOffset.ofHours(-5));

        TimeSlot zonedSlot = new TimeSlot(
                zonedStart, zonedStart.plusMinutes(30));
        TimeSlot legacySlot = new TimeSlot(
                LocalDateTime.of(2026, 11, 1, 1, 35),
                LocalDateTime.of(2026, 11, 1, 1, 45));

        assertFalse(zonedSlot.overlaps(legacySlot));
        assertFalse(legacySlot.overlaps(zonedSlot));
    }

    @Test
    void distinguishesRepeatedLocalTimesDuringAutumnDstTransition() {
        ZoneId zone = ZoneId.of("America/New_York");
        ZonedDateTime firstOneThirty = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 11, 1, 1, 30),
                zone, ZoneOffset.ofHours(-4));
        ZonedDateTime secondOneThirty = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 11, 1, 1, 30),
                zone, ZoneOffset.ofHours(-5));

        TimeSlot first = new TimeSlot(
                firstOneThirty, firstOneThirty.plusMinutes(30));
        TimeSlot second = new TimeSlot(
                secondOneThirty, secondOneThirty.plusMinutes(30));

        assertEquals(first.start(), second.start());
        assertFalse(first.overlaps(second));
        assertEquals(30, first.durationMinutes());
        assertEquals(30, second.durationMinutes());
    }
    @Test
    void appointmentUsesZonedTimeAcrossDstTransition() {
        ZoneId zone = ZoneId.of("America/New_York");
        ZonedDateTime start = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 3, 8, 1, 50),
                zone, ZoneOffset.ofHours(-5));

        AppointmentType type =
                new AppointmentType(1L, "Follow-up", 20, Set.of("Cardiology"));

        Appointment appointment = new Appointment(
                1L,
                new Patient(1L, "Test Patient"),
                new Provider(1L, "Dr Rao", "Cardiology"),
                new Room(1L, "Room 1"),
                null,
                type,
                start,
                5);

        assertEquals(20, appointment.getAppointmentSlot().durationMinutes());
        assertEquals(25, appointment.getReservedSlot().durationMinutes());
        assertEquals(start, appointment.cancel().zonedStart());
        assertEquals(start, appointment.markForRescheduling().zonedStart());
    }

    @Test
    void appointmentCanRepresentEitherOccurrenceOfRepeatedDstTime() {
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDateTime repeatedTime = LocalDateTime.of(2026, 11, 1, 1, 30);

        ZonedDateTime firstOccurrence = ZonedDateTime.ofLocal(
                repeatedTime, zone, ZoneOffset.ofHours(-4));
        ZonedDateTime secondOccurrence = ZonedDateTime.ofLocal(
                repeatedTime, zone, ZoneOffset.ofHours(-5));

        AppointmentType type =
                new AppointmentType(1L, "Follow-up", 20, Set.of("Cardiology"));
        Patient patient = new Patient(1L, "Test Patient");
        Provider provider = new Provider(1L, "Dr Rao", "Cardiology");
        Room room = new Room(1L, "Room 1");

        Appointment first = new Appointment(
                1L, patient, provider, room, null, type, firstOccurrence, 5);
        Appointment second = new Appointment(
                2L, patient, provider, room, null, type, secondOccurrence, 5);

        assertEquals(first.start(), second.start());
        assertNotEquals(first.zonedStart().toInstant(), second.zonedStart().toInstant());
        assertFalse(first.getReservedSlot().overlaps(second.getReservedSlot()));
    }
    @Test
    void rejectsBlankPatientName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Patient(1L, " "));
    }
}