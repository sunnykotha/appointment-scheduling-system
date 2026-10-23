package com.example.appointments;

public record Clinic(
        Long id,
        String name,
        int dailyCapacity,
        int overbookingPercentage) {

    public Clinic {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Clinic name is required");
        }
        if (dailyCapacity <= 0) {
            throw new IllegalArgumentException(
                    "Daily capacity must be positive");
        }
        if (overbookingPercentage < 0) {
            throw new IllegalArgumentException(
                    "Overbooking percentage cannot be negative");
        }
    }

    public int maximumDailyAppointments() {
        int extraAppointments = (int) Math.ceil(
                dailyCapacity * (overbookingPercentage / 100.0));
        return dailyCapacity + extraAppointments;
    }
}
