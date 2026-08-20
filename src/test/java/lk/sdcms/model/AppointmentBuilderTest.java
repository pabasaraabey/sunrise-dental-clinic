package lk.sdcms.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Builder exists to make invalid Appointments unconstructable. These tests
 * verify that, rather than merely that the getters return what was set.
 */
class AppointmentBuilderTest {

    private Appointment.Builder validBuilder() {
        Patient patient = new Patient();
        patient.setPatientId(1L);

        Dentist dentist = new Dentist();
        dentist.setDentistId(1L);

        Treatment treatment = new Treatment();
        treatment.setTreatmentId(1L);

        return Appointment.builder()
                .appointmentNo("APT-20260821-0001")
                .patient(patient)
                .dentist(dentist)
                .treatment(treatment)
                .date(LocalDate.now().plusDays(1))
                .time(LocalTime.of(10, 0))
                .createdBy(1L);
    }

    @Test
    @DisplayName("A fully populated builder produces an appointment")
    void buildsWhenComplete() {
        Appointment a = validBuilder().build();
        assertNotNull(a);
        assertEquals(AppointmentStatus.SCHEDULED, a.getStatus());
    }

    @Test
    @DisplayName("Building without a dentist is rejected at construction")
    void missingDentistIsRejected() {
        Patient patient = new Patient();
        patient.setPatientId(1L);
        Treatment treatment = new Treatment();
        treatment.setTreatmentId(1L);

        assertThrows(NullPointerException.class, () ->
                Appointment.builder()
                        .patient(patient)
                        .treatment(treatment)
                        .date(LocalDate.now().plusDays(1))
                        .time(LocalTime.of(10, 0))
                        .createdBy(1L)
                        .build());
    }

    @Test
    @DisplayName("Appointment numbers follow the APT-YYYYMMDD-NNNN format")
    void appointmentNumberFormat() {
        String no = Appointment.buildAppointmentNo(LocalDate.of(2026, 8, 21), 42);
        assertEquals("APT-20260821-0042", no);
    }

    @Test
    @DisplayName("An appointment well in the future can be cancelled")
    void futureAppointmentIsCancellable() {
        Appointment a = validBuilder()
                .date(LocalDate.now().plusDays(3))
                .build();
        assertTrue(a.isCancellable());
    }

    @Test
    @DisplayName("An appointment inside the two-hour window cannot be cancelled")
    void imminentAppointmentIsNotCancellable() {
        Appointment a = validBuilder()
                .date(LocalDate.now())
                .time(LocalTime.now().plusMinutes(30))
                .build();
        assertFalse(a.isCancellable());
    }

    @Test
    @DisplayName("An already-cancelled appointment cannot be cancelled again")
    void cancelledAppointmentIsNotCancellable() {
        Appointment a = validBuilder()
                .date(LocalDate.now().plusDays(3))
                .status(AppointmentStatus.CANCELLED)
                .build();
        assertFalse(a.isCancellable());
    }
}
