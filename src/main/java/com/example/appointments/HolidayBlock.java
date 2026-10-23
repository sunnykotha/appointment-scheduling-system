package com.example.appointments;

import java.time.LocalDate;
import java.util.Objects;

public record HolidayBlock(LocalDate date, Long clinicId) {

    public HolidayBlock {
        Objects.requireNonNull(date, "Holiday date is required");
    }

    public boolean blocks(Appointment appointment) {
        Objects.requireNonNull(appointment, "Appointment is required");

        if (!date.equals(appointment.start().toLocalDate())) {
            return false;
        }

        if (clinicId == null) {
            return true;
        }

        return appointment.clinic() != null
                && clinicId.equals(appointment.clinic().id());
    }
}