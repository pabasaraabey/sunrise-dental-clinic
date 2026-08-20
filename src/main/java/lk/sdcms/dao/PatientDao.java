package lk.sdcms.dao;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.model.Gender;
import lk.sdcms.model.Patient;
import lk.sdcms.util.DBConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the patients table.
 *
 * <p>Note the pairs of methods: one that opens its own connection, and one
 * that accepts a {@link Connection} from the caller. The second form exists so
 * that a patient insert can share a transaction with an appointment insert —
 * a DAO that always opens its own connection cannot participate in a caller's
 * transaction, and a booking that half-succeeds would leave an orphaned
 * patient record behind.
 */
public class PatientDao {

    private static final String SELECT_BASE = """
            SELECT patient_id, full_name, address, contact_no, email,
                   date_of_birth, gender, medical_notes, registered_date
            FROM patients
            """;

    public Optional<Patient> findByContactNo(String contactNo) {
        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            return findByContactNo(conn, contactNo);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up patient by contact", e);
        }
    }

    /** Transaction-participating variant. */
    public Optional<Patient> findByContactNo(Connection conn, String contactNo) {
        String sql = SELECT_BASE + " WHERE contact_no = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contactNo);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up patient by contact", e);
        }
    }

    public Optional<Patient> findById(Long patientId) {
        String sql = SELECT_BASE + " WHERE patient_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, patientId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up patient " + patientId, e);
        }
    }

    public List<Patient> findAll() {
        String sql = SELECT_BASE + " ORDER BY full_name";
        List<Patient> results = new ArrayList<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to list patients", e);
        }
    }

    public Patient insert(Patient patient) {
        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            return insert(conn, patient);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert patient", e);
        }
    }

    /** Transaction-participating variant. */
    public Patient insert(Connection conn, Patient patient) {
        String sql = """
                INSERT INTO patients
                    (full_name, address, contact_no, email,
                     date_of_birth, gender, medical_notes, registered_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_DATE)
                """;

        try (PreparedStatement ps =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, patient.getFullName());
            ps.setString(2, patient.getAddress());
            ps.setString(3, patient.getContactNo());
            ps.setString(4, patient.getEmail());
            ps.setDate(5, Date.valueOf(patient.getDateOfBirth()));
            ps.setString(6, patient.getGender().name());
            ps.setString(7, patient.getMedicalNotes());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    patient.setPatientId(keys.getLong(1));
                }
            }
            return patient;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert patient", e);
        }
    }

    private Patient mapRow(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setPatientId(rs.getLong("patient_id"));
        p.setFullName(rs.getString("full_name"));
        p.setAddress(rs.getString("address"));
        p.setContactNo(rs.getString("contact_no"));
        p.setEmail(rs.getString("email"));
        p.setDateOfBirth(rs.getDate("date_of_birth").toLocalDate());
        p.setGender(Gender.valueOf(rs.getString("gender")));
        p.setMedicalNotes(rs.getString("medical_notes"));

        Date registered = rs.getDate("registered_date");
        if (registered != null) {
            p.setRegisteredDate(registered.toLocalDate());
        }
        return p;
    }
}
