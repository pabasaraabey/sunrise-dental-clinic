package lk.sdcms.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Age is derived rather than stored, so these tests guard the boundary that
 * senior-citizen billing depends on.
 */
class PatientTest {

    private Patient patientBornYearsAgo(int years, int extraDays) {
        Patient p = new Patient();
        p.setDateOfBirth(LocalDate.now().minusYears(years).minusDays(extraDays));
        return p;
    }

    @Test
    @DisplayName("Age is derived correctly from date of birth")
    void ageIsDerived() {
        assertEquals(40, patientBornYearsAgo(40, 0).getAge());
    }

    @Test
    @DisplayName("A patient aged exactly 65 qualifies for the concession")
    void exactlySixtyFiveQualifies() {
        assertTrue(patientBornYearsAgo(65, 0).isSeniorCitizen());
    }

    @Test
    @DisplayName("A patient one day short of 65 does not qualify")
    void oneDayShortDoesNotQualify() {
        Patient p = new Patient();
        p.setDateOfBirth(LocalDate.now().minusYears(65).plusDays(1));
        assertFalse(p.isSeniorCitizen());
    }

    @Test
    @DisplayName("Age cannot be derived without a date of birth")
    void missingDateOfBirthIsRejected() {
        assertThrows(IllegalStateException.class, () -> new Patient().getAge());
    }
}
