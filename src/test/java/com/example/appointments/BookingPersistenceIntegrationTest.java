package com.example.appointments;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:appointment-integration;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingPersistenceIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PersistentBookingService persistentBookingService;

    @Autowired
    private AppointmentReservationRepository reservations;

    private Appointment appointment(
            Long appointmentId,
            Long patientId,
            Long providerId,
            Long roomId,
            LocalDateTime start) {
        return new Appointment(
                appointmentId,
                new Patient(patientId, "Patient " + patientId),
                new Provider(providerId, "Provider " + providerId, "General"),
                new Room(roomId, "Room " + roomId),
                null,
                new AppointmentType(1L, "Consultation", 20, Set.of()),
                start,
                5);
    }

    @Test
    void springManagedBookingServicePersistsSuccessfulBooking() {
        Appointment appointment = appointment(
                91001L, 91001L, 91001L, 91001L,
                LocalDateTime.of(2027, 1, 10, 10, 0));

        assertTrue(bookingService.tryBook(appointment));

        AppointmentReservationEntity saved = reservations
                .findBySourceAppointmentId(91001L)
                .orElseThrow();

        assertFalse(saved.isCancelled());
    }

    @Test
    void cancellingThroughBookingServiceAlsoCancelsDatabaseReservation() {
        Appointment appointment = appointment(
                91002L, 91002L, 91002L, 91002L,
                LocalDateTime.of(2027, 1, 11, 10, 0));

        assertTrue(bookingService.tryBook(appointment));
        assertTrue(bookingService.cancelBooking(91002L));

        AppointmentReservationEntity saved = reservations
                .findBySourceAppointmentId(91002L)
                .orElseThrow();

        assertTrue(saved.isCancelled());
        assertFalse(bookingService.cancelBooking(91002L));
    }

    @Test
    void persistentServiceRejectsConflictingBookingFromAnotherCaller() {
        LocalDateTime start = LocalDateTime.of(2027, 1, 12, 10, 0);

        Appointment first = appointment(91003L, 91003L, 91003L, 91003L, start);
        Appointment conflicting = appointment(
                91004L, 91004L, 91003L, 91004L, start);

        assertTrue(persistentBookingService.tryBook(first));
        assertFalse(persistentBookingService.tryBook(conflicting));

        assertTrue(reservations.findBySourceAppointmentId(91003L).isPresent());
        assertTrue(reservations.findBySourceAppointmentId(91004L).isEmpty());
    }

    @Test
    void persistentServiceRejectsSamePatientAtDifferentClinics() {
        LocalDateTime start = LocalDateTime.of(2027, 1, 13, 10, 0);

        Appointment first = new Appointment(
                91005L,
                new Patient(92005L, "Shared Patient"),
                new Provider(93005L, "Provider A", "General"),
                new Room(94005L, "Room A"),
                null,
                new AppointmentType(95005L, "Consultation", 20, Set.of()),
                start,
                5,
                new Clinic(96005L, "Clinic A", 20, 0));

        Appointment second = new Appointment(
                91006L,
                new Patient(92005L, "Shared Patient"),
                new Provider(93006L, "Provider B", "General"),
                new Room(94006L, "Room B"),
                null,
                new AppointmentType(95006L, "Consultation", 20, Set.of()),
                start.plusMinutes(10),
                5,
                new Clinic(96006L, "Clinic B", 20, 0));

        assertTrue(persistentBookingService.tryBook(first));
        assertFalse(persistentBookingService.tryBook(second));

        assertTrue(reservations.findBySourceAppointmentId(91005L).isPresent());
        assertTrue(reservations.findBySourceAppointmentId(91006L).isEmpty());
    }

    @Test
    void concurrentConflictingBookingsAllowOnlyOneWinner() throws Exception {
        java.util.concurrent.CountDownLatch ready =
                new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch start =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(2);

        LocalDateTime slot = LocalDateTime.of(2027, 1, 14, 10, 0);
        Appointment first = appointment(91007L, 91007L, 93007L, 94007L, slot);
        Appointment second = appointment(91008L, 91008L, 93007L, 94007L, slot);

        try {
            java.util.concurrent.Callable<Boolean> bookFirst = () -> {
                ready.countDown();
                start.await();
                return persistentBookingService.tryBook(first);
            };
            java.util.concurrent.Callable<Boolean> bookSecond = () -> {
                ready.countDown();
                start.await();
                return persistentBookingService.tryBook(second);
            };

            java.util.concurrent.Future<Boolean> firstResult =
                    executor.submit(bookFirst);
            java.util.concurrent.Future<Boolean> secondResult =
                    executor.submit(bookSecond);

            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();

            boolean firstBooked =
                    firstResult.get(15, java.util.concurrent.TimeUnit.SECONDS);
            boolean secondBooked =
                    secondResult.get(15, java.util.concurrent.TimeUnit.SECONDS);

            assertNotEquals(firstBooked, secondBooked,
                    "Exactly one conflicting booking should succeed");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}