package lk.sdcms.service;

import lk.sdcms.dao.*;
import lk.sdcms.dto.AppointmentRequest;
import lk.sdcms.exception.*;
import lk.sdcms.model.*;
import lk.sdcms.pattern.BookingObserver;
import lk.sdcms.util.DBConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Booking, searching and cancelling appointments.
 *
 * <p>This is where the clinic's core problem is solved. The scenario names
 * double booking as the first symptom of the paper process, and the defence
 * against it is built here in two layers — see {@link #book}.
 *
 * <p>All DAOs arrive through the constructor. That is what allows the booking
 * rules to be unit tested with mocks, without a database.
 */
public class AppointmentService {

    /** Clinic opening time; the first bookable slot. */
    public static final LocalTime OPENING_TIME = LocalTime.of(8, 0);

    /** Clinic closing time; no slot may start at or after this. */
    public static final LocalTime CLOSING_TIME = LocalTime.of(20, 0);

    /** Slot granularity in minutes. */
    public static final int SLOT_MINUTES = 30;

    /** How far ahead a booking may be made. */
    public static final int MAX_ADVANCE_DAYS = 90;

    private final AppointmentDao appointmentDao;
    private final PatientDao     patientDao;
    private final DentistDao     dentistDao;
    private final TreatmentDao   treatmentDao;

    private final List<BookingObserver> observers = new ArrayList<>();

    public AppointmentService(AppointmentDao appointmentDao,
                              PatientDao patientDao,
                              DentistDao dentistDao,
                              TreatmentDao treatmentDao) {
        this.appointmentDao = appointmentDao;
        this.patientDao     = patientDao;
        this.dentistDao     = dentistDao;
        this.treatmentDao   = treatmentDao;
    }

    public void registerObserver(BookingObserver observer) {
        observers.add(observer);
    }

    /**
     * Books an appointment.
     *
     * <p><b>Double booking is prevented at two levels, and both are needed.</b>
     *
     * <p>The availability check below is not sufficient on its own. Two
     * receptionists booking the same slot at nearly the same moment will both
     * run it, both find the slot free because neither has committed, and both
     * proceed to insert. Check-then-act is not atomic, and the interval between
     * the check and the commit is precisely where the collision happens. No
     * amount of care in this method closes that window.
     *
     * <p>The composite UNIQUE constraint in the schema does close it: the
     * storage engine rejects the second insert outright. This method catches
     * that violation and reports it the same way as the friendly pre-check, so
     * the receptionist sees one clear message either way.
     *
     * <p>So: the constraint is the guarantee, the check is the courtesy.
     *
     * <p>Patient creation and appointment insertion also share one transaction.
     * Were they separate, a failed booking would leave an orphaned patient
     * record behind — the sort of silent data corruption that is hard to notice
     * and harder to unpick later.
     */
    public Appointment book(AppointmentRequest request, Long createdBy) {

        validateRequest(request);

        Connection conn = null;
        try {
            conn = DBConnectionManager.getInstance().getConnection();
            conn.setAutoCommit(false);

            Dentist dentist = dentistDao.findById(conn, request.getDentistId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Dentist", request.getDentistId()));

            Treatment treatment = treatmentDao.findById(conn, request.getTreatmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Treatment", request.getTreatmentId()));

            if (!dentist.isWithinWorkingHours(request.getAppointmentTime())) {
                throw new ValidationException(
                        dentist.getFullName() + " works from "
                        + dentist.getAvailableFrom() + " to " + dentist.getAvailableTo());
            }

            // An existing patient is reused rather than duplicated. Duplicate
            // patient records are one of the failures the paper system produced.
            //
            // Bound to a separate final reference because `conn` is reassigned
            // in this method and so is not effectively final — a lambda cannot
            // capture it.
            final Connection tx = conn;
            Patient patient = patientDao
                    .findByContactNo(tx, request.getContactNo())
                    .orElseGet(() -> patientDao.insert(tx, newPatientFrom(request)));

            // Friendly pre-check. Not the guarantee — see the method comment.
            if (appointmentDao.existsForSlot(conn, dentist.getDentistId(),
                    request.getAppointmentDate(), request.getAppointmentTime())) {
                throw new SlotUnavailableException(dentist.getDentistId(),
                        request.getAppointmentDate(), request.getAppointmentTime());
            }

            int sequence = appointmentDao.nextSequenceForDate(
                    conn, request.getAppointmentDate());

            Appointment appointment = Appointment.builder()
                    .appointmentNo(Appointment.buildAppointmentNo(
                            request.getAppointmentDate(), sequence))
                    .patient(patient)
                    .dentist(dentist)
                    .treatment(treatment)
                    .date(request.getAppointmentDate())
                    .time(request.getAppointmentTime())
                    .status(AppointmentStatus.SCHEDULED)
                    .notes(request.getNotes())
                    .createdBy(createdBy)
                    .createdAt(LocalDateTime.now())
                    .build();

            try {
                appointmentDao.insert(conn, appointment);

            } catch (SQLIntegrityConstraintViolationException e) {
                // The constraint caught a concurrent booking the pre-check
                // could not see. Translated so the caller cannot tell the
                // difference between the two paths.
                conn.rollback();
                throw new SlotUnavailableException(dentist.getDentistId(),
                        request.getAppointmentDate(), request.getAppointmentTime());
            }

            conn.commit();

            notifyObservers(appointment);
            return appointment;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new DataAccessException("Booking failed", e);

        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;

        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * Cancels an appointment.
     *
     * <p>The record is retained with a CANCELLED status rather than deleted,
     * because the no-show and cancellation report depends on that history, as
     * would any later billing dispute.
     */
    public Appointment cancel(String appointmentNo) {
        Appointment appointment = appointmentDao.findByNo(appointmentNo)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentNo));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ValidationException("This appointment is already cancelled");
        }

        if (!appointment.isCancellable()) {
            throw new ValidationException(
                    "Appointments cannot be cancelled within "
                    + Appointment.CANCELLATION_CUTOFF_HOURS
                    + " hours of the scheduled time");
        }

        appointmentDao.updateStatus(appointmentNo, AppointmentStatus.CANCELLED);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        return appointment;
    }

    public Appointment findByNo(String appointmentNo) {
        return appointmentDao.findByNo(appointmentNo)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentNo));
    }

    public List<Appointment> findByDate(LocalDate date) {
        return appointmentDao.findByDate(date);
    }

    public List<Appointment> findByPatient(Long patientId) {
        return appointmentDao.findByPatient(patientId);
    }

    /**
     * Free slots for a dentist on a date, at {@link #SLOT_MINUTES} granularity,
     * bounded by both clinic hours and that dentist's own working hours.
     */
    public List<LocalTime> availableSlots(Long dentistId, LocalDate date) {
        Dentist dentist = dentistDao.findById(dentistId)
                .orElseThrow(() -> new ResourceNotFoundException("Dentist", dentistId));

        List<Appointment> booked = appointmentDao.findByDentistAndDate(dentistId, date);
        List<LocalTime> taken = booked.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED)
                .map(Appointment::getAppointmentTime)
                .toList();

        LocalTime from = dentist.getAvailableFrom().isAfter(OPENING_TIME)
                ? dentist.getAvailableFrom() : OPENING_TIME;
        LocalTime to = dentist.getAvailableTo().isBefore(CLOSING_TIME)
                ? dentist.getAvailableTo() : CLOSING_TIME;

        List<LocalTime> free = new ArrayList<>();
        boolean today = date.equals(LocalDate.now());

        for (LocalTime slot = from; slot.isBefore(to); slot = slot.plusMinutes(SLOT_MINUTES)) {
            if (taken.contains(slot)) {
                continue;
            }
            // A slot earlier today has already passed and cannot be booked.
            if (today && slot.isBefore(LocalTime.now())) {
                continue;
            }
            free.add(slot);
        }
        return free;
    }

    // -----------------------------------------------------------------

    private void validateRequest(AppointmentRequest r) {
        if (r == null) {
            throw new ValidationException("Request body is required");
        }
        requireText(r.getPatientName(), "Patient name");
        requireText(r.getAddress(),     "Address");
        requireText(r.getContactNo(),   "Contact number");

        if (r.getDateOfBirth() == null) {
            throw new ValidationException("Date of birth is required");
        }
        if (r.getDateOfBirth().isAfter(LocalDate.now())) {
            throw new ValidationException("Date of birth cannot be in the future");
        }
        if (r.getDentistId() == null) {
            throw new ValidationException("A dentist must be selected");
        }
        if (r.getTreatmentId() == null) {
            throw new ValidationException("A treatment must be selected");
        }
        if (r.getAppointmentDate() == null || r.getAppointmentTime() == null) {
            throw new ValidationException("Appointment date and time are required");
        }

        // Sri Lankan mobile and landline numbers: 10 digits beginning with 0.
        if (!r.getContactNo().matches("^0\\d{9}$")) {
            throw new ValidationException(
                    "Contact number must be 10 digits beginning with 0");
        }

        LocalDate date = r.getAppointmentDate();
        LocalTime time = r.getAppointmentTime();

        if (date.isBefore(LocalDate.now())) {
            throw new ValidationException("Appointments cannot be booked in the past");
        }
        if (date.isAfter(LocalDate.now().plusDays(MAX_ADVANCE_DAYS))) {
            throw new ValidationException(
                    "Appointments cannot be booked more than "
                    + MAX_ADVANCE_DAYS + " days ahead");
        }
        if (date.equals(LocalDate.now()) && time.isBefore(LocalTime.now())) {
            throw new ValidationException("That time has already passed today");
        }
        if (time.isBefore(OPENING_TIME) || !time.isBefore(CLOSING_TIME)) {
            throw new ValidationException(
                    "The clinic is open from " + OPENING_TIME + " to " + CLOSING_TIME);
        }
        if (time.getMinute() % SLOT_MINUTES != 0) {
            throw new ValidationException(
                    "Appointments start on " + SLOT_MINUTES + "-minute boundaries");
        }
    }

    private void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(label + " is required");
        }
    }

    private Patient newPatientFrom(AppointmentRequest r) {
        Patient p = new Patient();
        p.setFullName(r.getPatientName());
        p.setAddress(r.getAddress());
        p.setContactNo(r.getContactNo());
        p.setEmail(r.getEmail());
        p.setDateOfBirth(r.getDateOfBirth());
        p.setGender(r.getGender() == null
                ? Gender.OTHER
                : Gender.valueOf(r.getGender().toUpperCase()));
        return p;
    }

    /**
     * Observers run after the commit, and a failing observer must not undo a
     * booking that has already succeeded — the patient is standing at the desk.
     */
    private void notifyObservers(Appointment appointment) {
        for (BookingObserver observer : observers) {
            try {
                observer.onAppointmentBooked(appointment);
            } catch (RuntimeException e) {
                System.err.println("Observer failed: " + e.getMessage());
            }
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // Nothing useful can be done; the original exception matters more.
            }
        }
    }

    /**
     * Restores autocommit before returning the connection to the pool.
     * A pooled connection left with autocommit disabled will silently break
     * the next borrower, which is a genuinely difficult bug to trace.
     */
    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
