package com.example.appointments;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface AppointmentReservationRepository
        extends JpaRepository<AppointmentReservationEntity, Long> {

    java.util.Optional<AppointmentReservationEntity> findBySourceAppointmentId(Long sourceAppointmentId);

    @Query("""
            select r from AppointmentReservationEntity r
            where r.cancelled = false
              and r.reservedStart < :end
              and r.reservedEnd > :start
              and (
                    r.patientId = :patientId
                    or r.providerId = :providerId
                    or r.roomId = :roomId
                    or (:equipmentId is not null and r.equipmentId = :equipmentId)
                  )
            """)
    List<AppointmentReservationEntity> findConflicts(
            @Param("patientId") Long patientId,
            @Param("providerId") Long providerId,
            @Param("roomId") Long roomId,
            @Param("equipmentId") Long equipmentId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    List<AppointmentReservationEntity> findByClinicIdAndClinicServiceDateAndCancelledFalse(
            Long clinicId, LocalDate clinicServiceDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AppointmentReservationEntity r where r.id = :id")
    java.util.Optional<AppointmentReservationEntity> findByIdForUpdate(
            @Param("id") Long id);
}
