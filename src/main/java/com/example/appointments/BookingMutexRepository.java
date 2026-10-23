package com.example.appointments;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingMutexRepository
        extends JpaRepository<BookingMutexEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from BookingMutexEntity m where m.id = :id")
    Optional<BookingMutexEntity> findByIdForUpdate(@Param("id") Long id);
}
