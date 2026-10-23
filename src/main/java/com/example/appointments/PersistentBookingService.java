package com.example.appointments;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PersistentBookingService {

    private final AppointmentReservationRepository reservations;
    private final BookingMutexRepository mutexes;
    private final java.util.Set<HolidayBlock> registeredHolidayBlocks =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void registerHolidayBlock(HolidayBlock holidayBlock) {
        Objects.requireNonNull(holidayBlock, "Holiday block is required");
        registeredHolidayBlocks.add(holidayBlock);
    }

    public PersistentBookingService(
            AppointmentReservationRepository reservations,
            BookingMutexRepository mutexes) {
        this.reservations = reservations;
        this.mutexes = mutexes;
    }

    @Transactional
    public boolean tryBook(Appointment appointment) {
        return tryBook(appointment, List.of());
    }

    @Transactional
    public boolean tryBook(
            Appointment appointment, List<HolidayBlock> holidayBlocks) {
        Objects.requireNonNull(appointment, "Appointment is required");
        Objects.requireNonNull(holidayBlocks, "Holiday blocks are required");

        if (registeredHolidayBlocks.stream().anyMatch(holiday -> holiday.blocks(appointment))
                || holidayBlocks.stream().anyMatch(holiday -> holiday.blocks(appointment))) {
            return false;
        }

        mutexes.findByIdForUpdate(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Booking mutex row is missing"));

        Instant start = startInstant(appointment);
        Instant end = endInstant(appointment);

        List<AppointmentReservationEntity> conflicts =
                reservations.findConflicts(
                        appointment.patient().id(),
                        appointment.provider().id(),
                        appointment.room().id(),
                        appointment.equipment() == null
                                ? null : appointment.equipment().id(),
                        start,
                        end);

        if (!conflicts.isEmpty()) {
            return false;
        }

        Clinic clinic = appointment.clinic();
        Long clinicId = clinic == null ? null : clinic.id();

        if (clinic != null) {
            long count = reservations
                    .findByClinicIdAndClinicServiceDateAndCancelledFalse(
                            clinicId, appointment.start().toLocalDate())
                    .size();

            if (count >= clinic.maximumDailyAppointments()) {
                return false;
            }
        }

        AppointmentReservationEntity reservation =
                new AppointmentReservationEntity(
                        appointment.id(),
                        appointment.patient().id(),
                        appointment.provider().id(),
                        appointment.room().id(),
                        appointment.equipment() == null
                                ? null : appointment.equipment().id(),
                        appointment.appointmentType().id(),
                        clinicId,
                        start,
                        end,
                        appointment.start().toLocalDate());

        reservations.saveAndFlush(reservation);
        return true;
    }

    @Transactional
    public boolean cancelBooking(Long appointmentId) {
        Objects.requireNonNull(appointmentId, "Appointment ID is required");

        mutexes.findByIdForUpdate(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Booking mutex row is missing"));

        AppointmentReservationEntity reservation = reservations
                .findBySourceAppointmentId(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Appointment reservation not found: " + appointmentId));

        if (reservation.isCancelled()) {
            return false;
        }

        reservation.cancel();
        reservations.saveAndFlush(reservation);
        return true;
    }

    private Instant startInstant(Appointment appointment) {
        ZonedDateTime zonedStart = appointment.zonedStart();
        return zonedStart != null
                ? zonedStart.toInstant()
                : appointment.start()
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
    }

    private Instant endInstant(Appointment appointment) {
        ZonedDateTime zonedStart = appointment.zonedStart();
        int reservedMinutes = appointment.appointmentType().durationMinutes()
                + appointment.bufferMinutes();

        return zonedStart != null
                ? zonedStart.plusMinutes(reservedMinutes).toInstant()
                : appointment.start()
                        .plusMinutes(reservedMinutes)
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
    }
}
