package lk.sdcms.service;

import lk.sdcms.dao.*;
import lk.sdcms.dto.AppointmentRequest;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Booking rules, tested with mocked DAOs so no database is involved.
 *
 * <p>These cover validation and slot computation. The concurrency behaviour is
 * tested separately in AppointmentDaoConstraintTest, because proving the race
 * is closed requires a real database — a mock would simply return whatever it
 * was told to.
 */
class AppointmentServiceTest {

    private AppointmentDao appointmentDao;
    private PatientDao     patientDao;
    private DentistDao     dentistDao;
    private TreatmentDao   treatmentDao;
    private AppointmentService service;

    private Dentist dentist;

    @BeforeEach
    void setUp() {
        appointmentDao = mock(AppointmentDao.class);
        patientDao     = mock(PatientDao.class);
        dentistDao     = mock(DentistDao.class);
        treatmentDao   = mock(TreatmentDao.class);

        service = new AppointmentService(
                appointmentDao, patientDao, dentistDao, treatmentDao);

        dentist = new Dentist();
        dentist.setDentistId(1L);
        dentist.setFullName("Dr. Ashan Fernando");
        dentist.setAvailableFrom(LocalTime.of(8, 0));
        dentist.setAvailableTo(LocalTime.of(16, 0));
    }

    private AppointmentRequest validRequest() {
        AppointmentRequest r = new AppointmentRequest();
        r.setPatientName("Sunil Rathnayake");
        r.setAddress("42/3 Galle Road, Colombo 03");
        r.setContactNo("0771234567");
        r.setDateOfBirth(LocalDate.of(1988, 4, 17));
        r.setGender("MALE");
        r.setDentistId(1L);
        r.setTreatmentId(1L);
        r.setAppointmentDate(LocalDate.now().plusDays(3));
        r.setAppointmentTime(LocalTime.of(10, 0));
        return r;
    }

    // ---------- validation ----------

    @Test
    @DisplayName("A booking outside clinic hours is rejected")
    void outsideClinicHoursRejected() {
        AppointmentRequest r = validRequest();
        r.setAppointmentTime(LocalTime.of(21, 0));

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.book(r, 1L));
        assertTrue(e.getMessage().contains("open from"));
    }

    @Test
    @DisplayName("A booking at exactly closing time is rejected")
    void closingTimeIsExclusive() {
        AppointmentRequest r = validRequest();
        r.setAppointmentTime(LocalTime.of(20, 0));

        assertThrows(ValidationException.class, () -> service.book(r, 1L));
    }

    @Test
    @DisplayName("A booking at exactly opening time is accepted by validation")
    void openingTimeIsInclusive() {
        AppointmentRequest r = validRequest();
        r.setAppointmentTime(LocalTime.of(8, 0));

        // Validation must pass; the call then fails on the mocked DAO, which is
        // fine — the assertion here is that it is not a ValidationException.
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.book(r, 1L));
        assertFalse(thrown instanceof ValidationException,
                "08:00 should pass validation, got: " + thrown.getMessage());
    }

    @Test
    @DisplayName("A booking in the past is rejected")
    void pastDateRejected() {
        AppointmentRequest r = validRequest();
        r.setAppointmentDate(LocalDate.now().minusDays(1));

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.book(r, 1L));
        assertTrue(e.getMessage().toLowerCase().contains("past"));
    }

    @Test
    @DisplayName("A booking beyond the advance limit is rejected")
    void tooFarAheadRejected() {
        AppointmentRequest r = validRequest();
        r.setAppointmentDate(LocalDate.now().plusDays(
                AppointmentService.MAX_ADVANCE_DAYS + 1));

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.book(r, 1L));
        assertTrue(e.getMessage().contains("90"));
    }

    @Test
    @DisplayName("A time off the 30-minute boundary is rejected")
    void offBoundaryTimeRejected() {
        AppointmentRequest r = validRequest();
        r.setAppointmentTime(LocalTime.of(10, 17));

        assertThrows(ValidationException.class, () -> service.book(r, 1L));
    }

    @Test
    @DisplayName("A malformed contact number is rejected")
    void malformedContactRejected() {
        AppointmentRequest r = validRequest();
        r.setContactNo("77123456");

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.book(r, 1L));
        assertTrue(e.getMessage().contains("10 digits"));
    }

    @Test
    @DisplayName("A missing patient name is rejected")
    void missingNameRejected() {
        AppointmentRequest r = validRequest();
        r.setPatientName("  ");

        assertThrows(ValidationException.class, () -> service.book(r, 1L));
    }

    @Test
    @DisplayName("A future date of birth is rejected")
    void futureDateOfBirthRejected() {
        AppointmentRequest r = validRequest();
        r.setDateOfBirth(LocalDate.now().plusDays(1));

        assertThrows(ValidationException.class, () -> service.book(r, 1L));
    }

    @Test
    @DisplayName("Validation runs before any database access")
    void validationPrecedesDatabaseAccess() {
        AppointmentRequest r = validRequest();
        r.setContactNo("bad");

        assertThrows(ValidationException.class, () -> service.book(r, 1L));

        // Nothing should have been touched — rejecting early avoids borrowing
        // a pooled connection for a request that was never going to succeed.
        verifyNoInteractions(appointmentDao, patientDao, dentistDao, treatmentDao);
    }

    // ---------- availability ----------

    @Test
    @DisplayName("Available slots exclude times already booked")
    void bookedSlotsAreExcluded() {
        LocalDate date = LocalDate.now().plusDays(5);

        Patient patient = new Patient();
        patient.setPatientId(1L);
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));

        Treatment treatment = new Treatment(1L, "Check-up", new BigDecimal("2500.00"));

        Appointment existing = Appointment.builder()
                .appointmentNo("APT-TEST-0001")
                .patient(patient).dentist(dentist).treatment(treatment)
                .date(date).time(LocalTime.of(9, 0))
                .status(AppointmentStatus.SCHEDULED)
                .createdBy(1L)
                .build();

        when(dentistDao.findById(1L)).thenReturn(Optional.of(dentist));
        when(appointmentDao.findByDentistAndDate(1L, date)).thenReturn(List.of(existing));

        List<LocalTime> slots = service.availableSlots(1L, date);

        assertFalse(slots.contains(LocalTime.of(9, 0)), "Booked slot should be absent");
        assertTrue(slots.contains(LocalTime.of(9, 30)), "Adjacent slot should remain");
    }

    @Test
    @DisplayName("Available slots respect the dentist's own working hours")
    void slotsRespectDentistHours() {
        LocalDate date = LocalDate.now().plusDays(5);

        when(dentistDao.findById(1L)).thenReturn(Optional.of(dentist));
        when(appointmentDao.findByDentistAndDate(1L, date)).thenReturn(List.of());

        List<LocalTime> slots = service.availableSlots(1L, date);

        // This dentist finishes at 16:00, even though the clinic runs to 20:00.
        assertTrue(slots.stream().allMatch(t -> t.isBefore(LocalTime.of(16, 0))));
        assertTrue(slots.contains(LocalTime.of(15, 30)));
        assertFalse(slots.contains(LocalTime.of(16, 0)));
    }

    @Test
    @DisplayName("A cancelled appointment frees its slot again")
    void cancelledAppointmentFreesSlot() {
        LocalDate date = LocalDate.now().plusDays(5);

        Patient patient = new Patient();
        patient.setPatientId(1L);
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));

        Treatment treatment = new Treatment(1L, "Check-up", new BigDecimal("2500.00"));

        Appointment cancelled = Appointment.builder()
                .appointmentNo("APT-TEST-0002")
                .patient(patient).dentist(dentist).treatment(treatment)
                .date(date).time(LocalTime.of(9, 0))
                .status(AppointmentStatus.CANCELLED)
                .createdBy(1L)
                .build();

        when(dentistDao.findById(1L)).thenReturn(Optional.of(dentist));
        when(appointmentDao.findByDentistAndDate(1L, date)).thenReturn(List.of(cancelled));

        assertTrue(service.availableSlots(1L, date).contains(LocalTime.of(9, 0)),
                "A cancelled booking must release the slot for re-sale");
    }
}
