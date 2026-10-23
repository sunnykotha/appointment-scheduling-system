package com.example.appointments;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LeaveBlockTest {

    @Test
    void blocksOverlappingAppointmentForSameProvider() {
        var provider = new Provider(1L, "Dr Rao", "Cardiology");
        var patient = new Patient(1L, "Patient One");
        var room = new Room(1L, "Room 1");
        var type = new AppointmentType(
                1L, "Follow-up", 20, Set.of("Cardiology"));

        var leaveStart = LocalDateTime.of(2026, 10, 23, 10, 0);
        var leave = new LeaveBlock(
                1L, provider, leaveStart, leaveStart.plusHours(1), "Holiday");

        var appointment = new Appointment(
                1L, patient, provider, room, null, type,
                leaveStart.plusMinutes(30), 5);

        assertTrue(leave.blocks(appointment));
    }

    @Test
    void doesNotBlockAppointmentOutsideLeaveInterval() {
        var provider = new Provider(1L, "Dr Rao", "Cardiology");
        var patient = new Patient(1L, "Patient One");
        var room = new Room(1L, "Room 1");
        var type = new AppointmentType(
                1L, "Follow-up", 20, Set.of("Cardiology"));

        var leaveStart = LocalDateTime.of(2026, 10, 23, 10, 0);
        var leave = new LeaveBlock(
                1L, provider, leaveStart, leaveStart.plusHours(1), "Holiday");

        var appointment = new Appointment(
                1L, patient, provider, room, null, type,
                leaveStart.plusHours(2), 5);

        assertFalse(leave.blocks(appointment));
    }

    @Test
    void doesNotBlockAppointmentForAnotherProvider() {
        var providerOnLeave = new Provider(1L, "Dr Rao", "Cardiology");
        var otherProvider = new Provider(2L, "Dr Kumar", "Cardiology");
        var patient = new Patient(1L, "Patient One");
        var room = new Room(1L, "Room 1");
        var type = new AppointmentType(
                1L, "Follow-up", 20, Set.of("Cardiology"));

        var start = LocalDateTime.of(2026, 10, 23, 10, 0);
        var leave = new LeaveBlock(
                1L, providerOnLeave, start, start.plusHours(1), "Leave");

        var appointment = new Appointment(
                1L, patient, otherProvider, room, null, type, start, 5);

        assertFalse(leave.blocks(appointment));
    }

    @Test
    void rejectsLeaveEndThatIsNotAfterStart() {
        var provider = new Provider(1L, "Dr Rao", "Cardiology");
        var start = LocalDateTime.of(2026, 10, 23, 10, 0);

        assertThrows(IllegalArgumentException.class, () ->
                new LeaveBlock(1L, provider, start, start, "Holiday"));
    }

    @Test
    void findsAppointmentsRequiringRescheduling() {
        var provider = new Provider(1L, "Dr Rao", "Cardiology");
        var otherProvider = new Provider(2L, "Dr Kumar", "Cardiology");
        var patient1 = new Patient(1L, "Patient One");
        var patient2 = new Patient(2L, "Patient Two");
        var room = new Room(1L, "Room 1");
        var type = new AppointmentType(
                1L, "Follow-up", 20, Set.of("Cardiology"));

        var start = LocalDateTime.of(2026, 10, 23, 10, 0);
        var leave = new LeaveBlock(
                1L, provider, start, start.plusHours(1), "Holiday");

        var affected = new Appointment(
                1L, patient1, provider, room, null, type,
                start.plusMinutes(10), 5);
        var unaffected = new Appointment(
                2L, patient2, otherProvider, room, null, type,
                start.plusMinutes(10), 5);
        var cancelled = new Appointment(
                3L, patient2, provider, new Room(2L, "Room 2"),
                null, type, start.plusMinutes(10), 5).cancel();

        var result = leave.appointmentsToReschedule(
                java.util.List.of(affected, unaffected, cancelled));

        assertEquals(1, result.size());
        assertEquals(affected.id(), result.get(0).id());
        assertTrue(result.get(0).reschedulingRequired());
        assertFalse(affected.reschedulingRequired());
    }}