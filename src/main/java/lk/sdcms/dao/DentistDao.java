package lk.sdcms.dao;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.model.Dentist;
import lk.sdcms.util.DBConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the dentists table.
 *
 * <p>Dentists are maintained by reception rather than being system users, so
 * this DAO supports the full lifecycle: add, amend, and retire.
 *
 * <p>Note there is no delete. A dentist referenced by historical appointments
 * cannot be removed without orphaning those records and destroying the workload
 * and revenue reports. Retiring sets {@code is_active = FALSE}, which removes
 * them from the booking dropdown while leaving history intact.
 */
public class DentistDao {

    private static final String SELECT_BASE = """
            SELECT dentist_id, full_name, license_no, specialization, contact_no,
                   available_from, available_to, consultation_room, is_active
            FROM dentists
            """;

    public Optional<Dentist> findById(Long dentistId) {
        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            return findById(conn, dentistId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up dentist " + dentistId, e);
        }
    }

    public Optional<Dentist> findById(Connection conn, Long dentistId) {
        String sql = SELECT_BASE + " WHERE dentist_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, dentistId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up dentist " + dentistId, e);
        }
    }

    /** Active dentists only — used to populate the booking form. */
    public List<Dentist> findAllActive() {
        return query(SELECT_BASE + " WHERE is_active = TRUE ORDER BY full_name");
    }

    /** Every dentist including retired ones — used by the management screen. */
    public List<Dentist> findAll() {
        return query(SELECT_BASE + " ORDER BY is_active DESC, full_name");
    }

    public Dentist insert(Dentist dentist) {
        String sql = """
                INSERT INTO dentists
                    (full_name, license_no, specialization, contact_no,
                     available_from, available_to, consultation_room, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                """;

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bindFields(ps, dentist);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    dentist.setDentistId(keys.getLong(1));
                }
            }
            return dentist;

        } catch (SQLIntegrityConstraintViolationException e) {
            // Duplicate licence number — a real data-entry error worth naming.
            throw new DataAccessException(
                    "A dentist with licence number " + dentist.getLicenseNo()
                    + " is already registered", e);

        } catch (SQLException e) {
            throw new DataAccessException("Failed to add dentist", e);
        }
    }

    public void update(Dentist dentist) {
        String sql = """
                UPDATE dentists SET
                    full_name = ?, license_no = ?, specialization = ?,
                    contact_no = ?, available_from = ?, available_to = ?,
                    consultation_room = ?
                WHERE dentist_id = ?
                """;

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindFields(ps, dentist);
            ps.setLong(8, dentist.getDentistId());
            ps.executeUpdate();

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DataAccessException(
                    "Licence number " + dentist.getLicenseNo()
                    + " belongs to another dentist", e);

        } catch (SQLException e) {
            throw new DataAccessException("Failed to update dentist", e);
        }
    }

    /**
     * Retires or reinstates a dentist. Never deletes — historical appointments
     * reference this row, and removing it would break the workload and revenue
     * reports as well as any receipt already issued.
     */
    public void setActive(Long dentistId, boolean active) {
        String sql = "UPDATE dentists SET is_active = ? WHERE dentist_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, active);
            ps.setLong(2, dentistId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to change dentist status", e);
        }
    }

    // -----------------------------------------------------------------

    private void bindFields(PreparedStatement ps, Dentist d) throws SQLException {
        ps.setString(1, d.getFullName());
        ps.setString(2, d.getLicenseNo());
        ps.setString(3, d.getSpecialization());
        ps.setString(4, d.getContactNo());
        ps.setTime(5, Time.valueOf(d.getAvailableFrom()));
        ps.setTime(6, Time.valueOf(d.getAvailableTo()));
        ps.setString(7, d.getConsultationRoom());
    }

    private List<Dentist> query(String sql) {
        List<Dentist> results = new ArrayList<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to list dentists", e);
        }
    }

    private Dentist mapRow(ResultSet rs) throws SQLException {
        Dentist d = new Dentist();
        d.setDentistId(rs.getLong("dentist_id"));
        d.setFullName(rs.getString("full_name"));
        d.setLicenseNo(rs.getString("license_no"));
        d.setSpecialization(rs.getString("specialization"));
        d.setContactNo(rs.getString("contact_no"));
        d.setAvailableFrom(rs.getTime("available_from").toLocalTime());
        d.setAvailableTo(rs.getTime("available_to").toLocalTime());
        d.setConsultationRoom(rs.getString("consultation_room"));
        d.setActive(rs.getBoolean("is_active"));
        return d;
    }
}
