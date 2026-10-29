# Appointment Scheduling System

A Java-based appointment scheduling project focused on conflict resolution, resource availability, recurring appointments, and waitlist management.

## Features

- Prevent overlapping bookings for providers, rooms, equipment, and the same patient.
- Reserve appointment duration plus the configured buffer.
- Validate appointment type duration and provider specialty restrictions.
- Support recurring appointment conflict policies: `SKIP`, `SHIFT`, and `REJECT`.
- Block provider leave and holiday periods and identify appointments affected by leave.
- Select waitlist candidates by urgency and waiting time, while considering appointment type and supported time preferences.
- Create time-limited waitlist holds, expire them, and offer the slot to the next eligible candidate.
- Apply late-cancellation strikes according to the configured cancellation rules.
- Enforce clinic daily capacity and configured overbooking limits.
- Persist bookings and protect against conflicting concurrent booking attempts.
- Handle zoned appointment times and daylight-saving-time cases.

## Technology

- Java 21
- Spring Boot
- Maven Wrapper
- JUnit 5
- H2 for persistence integration tests

## Prerequisites

- Install a Java 21 JDK.
- Use the Maven Wrapper included in the repository; a separate Maven installation is not required.

## Run the tests

From the project root in PowerShell, run:

```powershell
.\mvnw.cmd clean test
```

The test suite covers booking conflicts, recurring appointments, leave and holidays, clinic capacity, persistence, concurrency, cancellation, and waitlist behavior.

## Project structure

- `src/main/java/com/example/appointments/` — appointment domain models and scheduling services.
- `src/test/java/com/example/appointments/` — unit and integration tests.
- `pom.xml` — Maven project configuration.
