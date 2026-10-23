package com.example.appointments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "appointment_reservations")
public class AppointmentReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long sourceAppointmentId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long providerId;

    @Column(nullable = false)
    private Long roomId;

    private Long equipmentId;
    private Long appointmentTypeId;
    private Long clinicId;

    @Column(nullable = false)
    private Instant reservedStart;

    @Column(nullable = false)
    private Instant reservedEnd;

    @Column(nullable = false)
    private LocalDate clinicServiceDate;

    @Column(nullable = false)
    private boolean cancelled;

    @Version
    private Long version;

    protected AppointmentReservationEntity() {
    }

    public AppointmentReservationEntity(
            Long patientId,
            Long providerId,
            Long roomId,
            Long equipmentId,
            Long appointmentTypeId,
            Long clinicId,
            Instant reservedStart,
            Instant reservedEnd,
            LocalDate clinicServiceDate) {
        this(null, patientId, providerId, roomId, equipmentId,
                appointmentTypeId, clinicId, reservedStart, reservedEnd,
                clinicServiceDate);
    }

    public AppointmentReservationEntity(
            Long sourceAppointmentId,
            Long patientId,
            Long providerId,
            Long roomId,
            Long equipmentId,
            Long appointmentTypeId,
            Long clinicId,
            Instant reservedStart,
            Instant reservedEnd,
            LocalDate clinicServiceDate) {

        this.sourceAppointmentId = sourceAppointmentId;
        this.patientId = Objects.requireNonNull(patientId, "Patient ID is required");
        this.providerId = Objects.requireNonNull(providerId, "Provider ID is required");
        this.roomId = Objects.requireNonNull(roomId, "Room ID is required");
        this.reservedStart = Objects.requireNonNull(reservedStart, "Reserved start is required");
        this.reservedEnd = Objects.requireNonNull(reservedEnd, "Reserved end is required");
        this.clinicServiceDate = Objects.requireNonNull(
                clinicServiceDate, "Clinic service date is required");

        if (!reservedEnd.isAfter(reservedStart)) {
            throw new IllegalArgumentException("Reserved end must be after reserved start");
        }

        this.equipmentId = equipmentId;
        this.appointmentTypeId = appointmentTypeId;
        this.clinicId = clinicId;
        this.cancelled = false;
    }

    public Long getId() { return id; }
    public Long getSourceAppointmentId() { return sourceAppointmentId; }
    public Long getPatientId() { return patientId; }
    public Long getProviderId() { return providerId; }
    public Long getRoomId() { return roomId; }
    public Long getEquipmentId() { return equipmentId; }
    public Long getAppointmentTypeId() { return appointmentTypeId; }
    public Long getClinicId() { return clinicId; }
    public Instant getReservedStart() { return reservedStart; }
    public Instant getReservedEnd() { return reservedEnd; }
    public LocalDate getClinicServiceDate() { return clinicServiceDate; }
    public boolean isCancelled() { return cancelled; }
    public Long getVersion() { return version; }

    public void cancel() {
        this.cancelled = true;
    }
}
