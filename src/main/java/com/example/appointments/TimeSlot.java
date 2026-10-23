package com.example.appointments;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record TimeSlot(
        LocalDateTime start,
        LocalDateTime end,
        ZonedDateTime zonedStart,
        ZonedDateTime zonedEnd) {

    public TimeSlot(LocalDateTime start, LocalDateTime end) {
        this(start, end, null, null);
    }

    public TimeSlot(ZonedDateTime start, ZonedDateTime end) {
        this(
                start == null ? null : start.toLocalDateTime(),
                end == null ? null : end.toLocalDateTime(),
                start,
                end);
    }

    public TimeSlot {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end times are required");
        }

        if ((zonedStart == null) != (zonedEnd == null)) {
            throw new IllegalArgumentException(
                    "Both zoned start and end times must be provided");
        }

        if (zonedStart == null) {
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("End time must be after start time");
            }
        } else {
            if (!zonedStart.toLocalDateTime().equals(start)
                    || !zonedEnd.toLocalDateTime().equals(end)) {
                throw new IllegalArgumentException(
                        "Local and zoned times must match");
            }
            if (!zonedEnd.toInstant().isAfter(zonedStart.toInstant())) {
                throw new IllegalArgumentException(
                        "End time must be after start time");
            }
        }
    }

    public boolean overlaps(TimeSlot other) {
        if (other == null) {
            throw new IllegalArgumentException("Other time slot is required");
        }

        if (zonedStart != null && other.zonedStart != null) {
            return zonedStart.toInstant().isBefore(other.zonedEnd.toInstant())
                    && other.zonedStart.toInstant().isBefore(zonedEnd.toInstant());
        }

        if (zonedStart != null) {
            return zonedStart.toInstant().isBefore(
                            other.end.atZone(zonedStart.getZone()).toInstant())
                    && other.start.atZone(zonedStart.getZone()).toInstant()
                            .isBefore(zonedEnd.toInstant());
        }

        if (other.zonedStart != null) {
            return start.atZone(other.zonedStart.getZone()).toInstant()
                            .isBefore(other.zonedEnd.toInstant())
                    && other.zonedStart.toInstant()
                            .isBefore(end.atZone(other.zonedStart.getZone()).toInstant());
        }

        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public long durationMinutes() {
        if (zonedStart != null) {
            return Duration.between(
                    zonedStart.toInstant(), zonedEnd.toInstant()).toMinutes();
        }
        return Duration.between(start, end).toMinutes();
    }
}