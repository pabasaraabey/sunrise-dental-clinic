package lk.sdcms.dao;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.model.*;
import lk.sdcms.util.DBConnectionManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the appointments table.
 *
 * <p>This DAO carries the query the whole system turns on:
 * {@link #existsForSlot}. It is not, however, what guarantees correctness —
 * see the note on that method.
 */
public class AppointmentDao {

    private static final String SELECT_BASE = """
            SELECT a.appointment_no, a.appointment_date, a.appointment_time,
                   a.status, a.notes, a.created_by, a.created_at,
                   p.patient_id, p.full_name AS patient_name, p.address,
                   p.contact_no, p.email, p.date_of_birth, p.gender,
                   d.dentist_id, d.full_name AS dentist_name,
                   d.specialization, d.available_from, d.available_to,
                   t.treatment_id, t.name AS treatment_name,
                   t.base_cost, t.duration_minutes
            FROM appointments a
            JOIN patients   p  ON p.patient_id   = a.patient_id
            JOIN dentists   d  ON d.dentist_id   = a.dentist_id
            JOIN treatments t  ON t.treatment_id = a.treatment_id
            """;

    /**
     * Whether the given dentist already has a scheduled appointment in this slot.
     *
     * <p><b>This check does not prevent double booking on its own.</b> Two
     * concurrent requests can both call it, both receive {@code false} because
     * neither has committed yet, and both proceed to insert. Check-then-act is
     * not atomic and the gap between the two is exactly where the conflict
     * occurs.
     *
     * <p>What actually guarantees correctness is the composite UNIQUE
     * constraint {@code uq_dentist_slot} in the schema, which makes the storage
     * engine reject the second insert. This method exists so that the common,
     * uncontended case produces a clear message rather than a constraint
     * violation — the constraint is the guarantee, this is the courtesy.
     */
    public boolean existsForSlot(Connection conn, Long dentistId,
                                 LocalDate date, LocalTime time) {
        String sql = """
                SELECT COUNT(*) FROM appointments
                WHERE dentist_id = ?
                  AND appointment_date = ?
                  AND appointment_time = ?
                  AND status = 'SCHEDULED'
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, dentistId);
            ps.setDate(2, Date.valueOf(date));
            ps.setTime(3, Time.valueOf(time));

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to check slot availability", e);
        }
    }

    public boolean existsForSlot(Long dentistId, LocalDate date, LocalTime time) {
        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            return existsForSlot(conn, dentistId, date, time);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check slot availability", e);
        }
    }

    /**
     * Next sequence number for the given date, used to build the appointment
     * number. Counts all appointments on that date including cancelled ones,
     * so a number is never reused.
     */
    public int nextSequenceForDate(Connection conn, LocalDate date) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE appointment_date = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 1;
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to compute appointment sequence", e);
        }
    }

    /**
     * Inserts the appointment.
     *
     * <p>Deliberately lets {@link SQLIntegrityConstraintViolationException}
     * escape as itself rather than wrapping it, because the service layer needs
     * to distinguish a slot collision from any other database failure and
     * translate it into a 409 response.
     */
    public void insert(Connection conn, Appointment appointment)
            throws SQLIntegrityConstraintViolationException {

        String sql = """
                INSERT INTO appointments
                    (appointment_no, patient_id, dentist_id, treatment_id,
                     appointment_date, appointment_time, status, notes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appointment.getAppointmentNo());
            ps.setLong(2,   appointment.getPatient().getPatientId());
            ps.setLong(3,   appointment.getDentist().getDentistId());
            ps.setLong(4,   appointment.getTreatment().getTreatmentId());
            ps.setDate(5,   Date.valueOf(appointment.getAppointmentDate()));
            ps.setTime(6,   Time.valueOf(appointment.getAppointmentTime()));
            ps.setString(7, appointment.getStatus().name());
            ps.setString(8, appointment.getNotes());
            ps.setLong(9,   appointment.getCreatedBy());

            ps.executeUpdate();

        } catch (SQLIntegrityConstraintViolationException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert appointment", e);
        }
    }

    public Optional<Appointment> findByNo(String appointmentNo) {
        String sql = SELECT_BASE + " WHERE a.appointment_no = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, appointmentNo);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up appointment " + appointmentNo, e);
        }
    }

    public List<Appointment> findByDate(LocalDate date) {
        String sql = SELECT_BASE
                   + " WHERE a.appointment_date = ? ORDER BY a.appointment_time";
        return queryList(sql, ps -> ps.setDate(1, Date.valueOf(date)));
    }

    public List<Appointment> findByPatient(Long patientId) {
        String sql = SELECT_BASE
                   + " WHERE a.patient_id = ? ORDER BY a.appointment_date DESC";
        return queryList(sql, ps -> ps.setLong(1, patientId));
    }

    public List<Appointment> findByDentistAndDate(Long dentistId, LocalDate date) {
        String sql = SELECT_BASE
                   + " WHERE a.dentist_id = ? AND a.appointment_date = ?"
                   + " ORDER BY a.appointment_time";
        return queryList(sql, ps -> {
            ps.setLong(1, dentistId);
            ps.setDate(2, Date.valueOf(date));
        });
    }

    public void updateStatus(Connection conn, String appointmentNo,
                             AppointmentStatus status) {
        String sql = "UPDATE appointments SET status = ? WHERE appointment_no = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, appointmentNo);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to update appointment status", e);
        }
    }

    public void updateStatus(String appointmentNo, AppointmentStatus status) {
        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            updateStatus(conn, appointmentNo, status);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update appointment status", e);
        }
    }

    // -----------------------------------------------------------------

    @FunctionalInterface
    private interface ParamSetter {
        void set(PreparedStatement ps) throws SQLException;
    }

    private List<Appointment> queryList(String sql, ParamSetter setter) {
        List<Appointment> results = new ArrayList<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setter.set(ps);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
            return results;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to query appointments", e);
        }
    }

    private Appointment mapRow(ResultSet rs) throws SQLException {
        Patient patient = new Patient();
        patient.setPatientId(rs.getLong("patient_id"));
        patient.setFullName(rs.getString("patient_name"));
        patient.setAddress(rs.getString("address"));
        patient.setContactNo(rs.getString("contact_no"));
        patient.setEmail(rs.getString("email"));
        patient.setDateOfBirth(rs.getDate("date_of_birth").toLocalDate());
        patient.setGender(Gender.valueOf(rs.getString("gender")));

        Dentist dentist = new Dentist();
        dentist.setDentistId(rs.getLong("dentist_id"));
        dentist.setFullName(rs.getString("dentist_name"));
        dentist.setSpecialization(rs.getString("specialization"));
        dentist.setAvailableFrom(rs.getTime("available_from").toLocalTime());
        dentist.setAvailableTo(rs.getTime("available_to").toLocalTime());

        Treatment treatment = new Treatment();
        treatment.setTreatmentId(rs.getLong("treatment_id"));
        treatment.setName(rs.getString("treatment_name"));
        treatment.setBaseCost(rs.getBigDecimal("base_cost"));
        treatment.setDurationMinutes(rs.getInt("duration_minutes"));

        Appointment appointment = Appointment.builder()
                .appointmentNo(rs.getString("appointment_no"))
                .patient(patient)
                .dentist(dentist)
                .treatment(treatment)
                .date(rs.getDate("appointment_date").toLocalDate())
                .time(rs.getTime("appointment_time").toLocalTime())
                .status(AppointmentStatus.valueOf(rs.getString("status")))
                .notes(rs.getString("notes"))
                .createdBy(rs.getLong("created_by"))
                .build();

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            appointment.setCreatedAt(created.toLocalDateTime());
        }
        return appointment;
    }
}
