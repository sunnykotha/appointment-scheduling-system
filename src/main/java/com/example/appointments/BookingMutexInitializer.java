package com.example.appointments;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BookingMutexInitializer implements ApplicationRunner {

    private final BookingMutexRepository repository;

    public BookingMutexInitializer(BookingMutexRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!repository.existsById(1L)) {
            try {
                repository.saveAndFlush(new BookingMutexEntity(1L));
            } catch (org.springframework.dao.DataIntegrityViolationException exception) {
                // Another application instance may have initialized the row.
                if (!repository.existsById(1L)) {
                    throw exception;
                }
            }
        }
    }
}
