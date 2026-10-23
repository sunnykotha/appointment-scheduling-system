package com.example.appointments;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking_mutex")
public class BookingMutexEntity {

    @Id
    private Long id;

    protected BookingMutexEntity() {
    }

    public BookingMutexEntity(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
