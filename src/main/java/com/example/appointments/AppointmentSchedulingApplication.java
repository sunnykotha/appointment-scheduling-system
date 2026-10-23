package com.example.appointments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AppointmentSchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentSchedulingApplication.class, args);
    }
}