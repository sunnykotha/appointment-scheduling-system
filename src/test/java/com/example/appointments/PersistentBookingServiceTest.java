package com.example.appointments;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistentBookingServiceTest {

    @Mock
    private AppointmentReservationRepository reservations;

    @Mock
    private BookingMutexRepository mutexes;

    @InjectMocks
    private PersistentBookingService service;

    private Clinic clinic;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        clinic = new Clinic(1L, "Clinic A", 2, 0);

        appointment = new Appointment(
                101L,
                new Patient(1L, "Patient One"),
                new Provider(2L, "Provider One", "General"),
                new Room(3L, "Room One"),
                null,
                new AppointmentType(4L, "General", 20, Set.of()),
                LocalDateTime.of(2026, 10, 24, 10, 0),
                5,
                clinic);
    }

    private void allowMutex() {
        when(mutexes.findByIdForUpdate(1L))
                .thenReturn(Optional.of(new BookingMutexEntity(1L)));
    }

    @Test
    void savesAppointmentWhenNoConflictsExist() {
        allowMutex();
        when(reservations.findConflicts(
                anyLong(), anyLong(), anyLong(),
                any(), any(), any()))
                .thenReturn(List.of());
        when(reservations
                .findByClinicIdAndClinicServiceDateAndCancelledFalse(
                        1L, appointment.start().toLocalDate()))
                .thenReturn(List.of());

        assertTrue(service.tryBook(appointment));

        verify(reservations).saveAndFlush(any(AppointmentReservationEntity.class));
    }

    @Test
    void rejectsAppointmentWhenResourceOrPatientConflictExists() {
        allowMutex();
        when(reservations.findConflicts(
                anyLong(), anyLong(), anyLong(),
                any(), any(), any()))
                .thenReturn(List.of(new AppointmentReservationEntity(
                        1L, 2L, 3L, null, 4L, 1L,
                        appointment.start().atZone(
                                java.time.ZoneId.systemDefault()).toInstant(),
                        appointment.start().plusMinutes(25).atZone(
                                java.time.ZoneId.systemDefault()).toInstant(),
                        appointment.start().toLocalDate())));

        assertFalse(service.tryBook(appointment));

        verify(reservations, never())
                .saveAndFlush(any(AppointmentReservationEntity.class));
    }

    @Test
    void rejectsAppointmentWhenClinicCapacityIsReached() {
        allowMutex();
        when(reservations.findConflicts(
                anyLong(), anyLong(), anyLong(),
                any(), any(), any()))
                .thenReturn(List.of());
        when(reservations
                .findByClinicIdAndClinicServiceDateAndCancelledFalse(
                        1L, appointment.start().toLocalDate()))
                .thenReturn(List.of(
                        new AppointmentReservationEntity(
                                1L, 2L, 3L, null, 4L, 1L,
                                appointment.start().atZone(
                                        java.time.ZoneId.systemDefault()).toInstant(),
                                appointment.start().plusMinutes(25).atZone(
                                        java.time.ZoneId.systemDefault()).toInstant(),
                                appointment.start().toLocalDate()),
                        new AppointmentReservationEntity(
                                5L, 6L, 7L, null, 4L, 1L,
                                appointment.start().plusHours(1).atZone(
                                        java.time.ZoneId.systemDefault()).toInstant(),
                                appointment.start().plusHours(1).plusMinutes(25)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toInstant(),
                                appointment.start().toLocalDate())));

        assertFalse(service.tryBook(appointment));

        verify(reservations, never())
                .saveAndFlush(any(AppointmentReservationEntity.class));
    }

    @Test
    void cancellationMarksExistingReservationCancelled() {
        allowMutex();

        AppointmentReservationEntity reservation =
                new AppointmentReservationEntity(
                        101L, 1L, 2L, 3L, null, 4L, 1L,
                        appointment.start().atZone(
                                java.time.ZoneId.systemDefault()).toInstant(),
                        appointment.start().plusMinutes(25).atZone(
                                java.time.ZoneId.systemDefault()).toInstant(),
                        appointment.start().toLocalDate());

        when(reservations.findBySourceAppointmentId(101L))
                .thenReturn(Optional.of(reservation));

        assertTrue(service.cancelBooking(101L));
        assertTrue(reservation.isCancelled());

        verify(reservations).saveAndFlush(reservation);
    }
}
