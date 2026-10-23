package com.example.appointments;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecurringAppointmentServiceTest {

    private final Patient patient = new Patient(1L, "Test Patient");
    private final Provider provider = new Provider(1L, "Dr Rao", "Cardiology");
    private final Room room = new Room(1L, "Room 1");
    private final AppointmentType followUp =
            new AppointmentType(1L, "Follow-up", 20, Set.of("Cardiology"));
    private final LocalDateTime start =
            LocalDateTime.of(2026, 10, 23, 10, 0);

    private Appointment appointmentAt(LocalDateTime time) {
        return new Appointment(
                null, patient, provider, room, null, followUp, time, 5);
    }

    @Test
    void skipPolicyOmitsConflictingOccurrence() {
        var conflict = appointmentAt(start.plusDays(1));
        var service = new RecurringAppointmentService();

        var result = service.schedule(
                appointmentAt(start),
                3,
                1,
                List.of(conflict),
                RecurrencePolicy.SKIP);

        assertEquals(2, result.size());
        assertEquals(start, result.get(0).start());
        assertEquals(start.plusDays(2), result.get(1).start());
        assertNotNull(result.get(0).recurrenceSeriesId());
        assertEquals(
                result.get(0).recurrenceSeriesId(),
                result.get(1).recurrenceSeriesId());
    }

    @Test
    void shiftPolicyMovesConflictingOccurrenceToNextAvailableSlot() {
        var conflict = appointmentAt(start.plusDays(1));
        var service = new RecurringAppointmentService();

        var result = service.schedule(
                appointmentAt(start),
                3,
                1,
                List.of(conflict),
                RecurrencePolicy.SHIFT);

        assertEquals(3, result.size());
        assertEquals(start, result.get(0).start());
        assertEquals(start.plusDays(1).plusMinutes(25), result.get(1).start());
        assertEquals(start.plusDays(2), result.get(2).start());
    }

    @Test
    void shiftPolicySkipsMultipleConflictingReservations() {
        var firstConflict = appointmentAt(start.plusDays(1));
        var secondConflict = appointmentAt(
                start.plusDays(1).plusMinutes(25));
        var service = new RecurringAppointmentService();

        var result = service.schedule(
                appointmentAt(start),
                2,
                1,
                List.of(firstConflict, secondConflict),
                RecurrencePolicy.SHIFT);

        assertEquals(2, result.size());
        assertEquals(start, result.get(0).start());
        assertEquals(
                start.plusDays(1).plusMinutes(50),
                result.get(1).start());
    }
    @Test
    void zonedRecurrencePreservesLocalTimeAcrossDstTransition() {
        ZoneId zone = ZoneId.of("America/New_York");
        ZonedDateTime zonedStart = ZonedDateTime.ofLocal(
                LocalDateTime.of(2026, 3, 7, 10, 0),
                zone, ZoneOffset.ofHours(-5));

        Appointment template = new Appointment(
                null, patient, provider, room, null, followUp, zonedStart, 5);

        var result = new RecurringAppointmentService().schedule(
                template, 3, 1, List.of(), RecurrencePolicy.SKIP);

        assertEquals(3, result.size());
        assertEquals(10, result.get(0).zonedStart().getHour());
        assertEquals(10, result.get(1).zonedStart().getHour());
        assertEquals(10, result.get(2).zonedStart().getHour());
        assertEquals(ZoneOffset.ofHours(-5), result.get(0).zonedStart().getOffset());
        assertEquals(ZoneOffset.ofHours(-4), result.get(1).zonedStart().getOffset());
        assertEquals(20, result.get(1).getAppointmentSlot().durationMinutes());
    }
    @Test
    void rejectPolicyRejectsEntireSeriesWhenOccurrenceConflicts() {
        var conflict = appointmentAt(start.plusDays(1));
        var service = new RecurringAppointmentService();

        assertThrows(IllegalArgumentException.class, () ->
                service.schedule(
                        appointmentAt(start),
                        3,
                        1,
                        List.of(conflict),
                        RecurrencePolicy.REJECT));
    }
}