package com.example.appointments;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WaitlistEntryTest {

    private final Patient patient = new Patient(1L, "Test Patient");

    private final AppointmentType followUp =
            new AppointmentType(1L, "Follow-up", 20, Set.of("Cardiology"));

    private final LocalDateTime tenAm =
            LocalDateTime.of(2026, 10, 23, 10, 0);

    @Test
    void createsValidWaitlistEntry() {
        WaitlistEntry entry =
                new WaitlistEntry(1L, patient, followUp, 2, tenAm);

        assertEquals(patient, entry.patient());
        assertEquals(followUp, entry.appointmentType());
        assertEquals(2, entry.urgency());
        assertEquals(tenAm, entry.addedAt());
    }

    @Test
    void rejectsNegativeUrgency() {
        assertThrows(IllegalArgumentException.class,
                () -> new WaitlistEntry(1L, patient, followUp, -1, tenAm));
    }

    @Test
    void higherUrgencyRanksFirst() {
        WaitlistEntry lowUrgency =
                new WaitlistEntry(1L, patient, followUp, 1, tenAm);
        WaitlistEntry highUrgency =
                new WaitlistEntry(2L, patient, followUp, 3, tenAm.minusMinutes(5));

        List<WaitlistEntry> entries =
                new ArrayList<>(List.of(lowUrgency, highUrgency));

        entries.sort(Comparator.comparingInt(WaitlistEntry::urgency).reversed());

        assertEquals(highUrgency, entries.get(0));
        assertEquals(lowUrgency, entries.get(1));
    }

    @Test
    void earlierEntryRanksFirstWhenUrgencyIsEqual() {
        WaitlistEntry earlier =
                new WaitlistEntry(1L, patient, followUp, 2, tenAm.minusMinutes(10));
        WaitlistEntry later =
                new WaitlistEntry(2L, patient, followUp, 2, tenAm);

        List<WaitlistEntry> entries = new ArrayList<>(List.of(later, earlier));

        entries.sort(
                Comparator.comparingInt(WaitlistEntry::urgency).reversed()
                        .thenComparing(WaitlistEntry::addedAt));

        assertEquals(earlier, entries.get(0));
        assertEquals(later, entries.get(1));
    }
}