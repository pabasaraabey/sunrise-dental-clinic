package lk.sdcms.dao;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.model.Dentist;
import lk.sdcms.util.DBConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DentistDao {

    private static final String SELECT_BASE = """
            SELECT d.dentist_id, d.user_id, d.license_no, d.specialization,
                   d.available_from, d.available_to, d.consultation_room,
                   d.is_active, u.full_name, u.username
            FROM dentists d
            JOIN users u ON u.user_id = d.user_id
            """;

    public Optional<Dentist> findById(Long dentistId) {
        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            return findById(conn, dentistId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up dentist " + dentistId, e);
        }
    }

    public Optional<Dentist> findById(Connection conn, Long dentistId) {
        String sql = SELECT_BASE + " WHERE d.dentist_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, dentistId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up dentist " + dentistId, e);
        }
    }

    public List<Dentist> findAllActive() {
        String sql = SELECT_BASE + " WHERE d.is_active = TRUE ORDER BY u.full_name";
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
        d.setUserId(rs.getLong("user_id"));
        d.setLicenseNo(rs.getString("license_no"));
        d.setSpecialization(rs.getString("specialization"));
        d.setAvailableFrom(rs.getTime("available_from").toLocalTime());
        d.setAvailableTo(rs.getTime("available_to").toLocalTime());
        d.setConsultationRoom(rs.getString("consultation_room"));
        d.setActive(rs.getBoolean("is_active"));
        d.setFullName(rs.getString("full_name"));
        d.setUsername(rs.getString("username"));
        return d;
    }
}
