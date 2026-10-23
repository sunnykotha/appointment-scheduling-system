package com.example.appointments;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ClinicCapacityTest {

    private final AppointmentType type =
            new AppointmentType(1L, "General", 20, Set.of());

    private Appointment appointment(
            long index, Clinic clinic) {
        return new Appointment(
                index,
                new Patient(index, "Patient " + index),
                new Provider(index, "Provider " + index, "General"),
                new Room(index, "Room " + index),
                null,
                type,
                LocalDateTime.of(2026, 10, 24, 9, 0).plusHours(index),
                5,
                clinic);
    }

    @Test
    void rejectsBookingWhenNormalDailyCapacityIsReached() {
        Clinic clinic = new Clinic(1L, "Clinic A", 2, 0);
        BookingService service = new BookingService();

        assertTrue(service.tryBook(appointment(1, clinic)));
        assertTrue(service.tryBook(appointment(2, clinic)));
        assertFalse(service.tryBook(appointment(3, clinic)));
    }

    @Test
    void allowsConfiguredOverbookingUpToPercentageLimit() {
        Clinic clinic = new Clinic(1L, "Clinic A", 2, 50);
        BookingService service = new BookingService();

        assertTrue(service.tryBook(appointment(1, clinic)));
        assertTrue(service.tryBook(appointment(2, clinic)));
        assertTrue(service.tryBook(appointment(3, clinic)));
        assertFalse(service.tryBook(appointment(4, clinic)));
    }

    @Test
    void cancelledAppointmentsDoNotCountTowardDailyCapacity() {
        Clinic clinic = new Clinic(1L, "Clinic A", 2, 0);
        BookingService service = new BookingService();

        assertTrue(service.tryBook(appointment(1, clinic)));
        assertTrue(service.tryBook(appointment(2, clinic)));
        assertTrue(service.cancelBooking(1L));
        assertTrue(service.tryBook(appointment(3, clinic)));
    }
}
