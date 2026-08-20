package lk.sdcms.dao;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.model.Treatment;
import lk.sdcms.util.DBConnectionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TreatmentDao {

    private static final String SELECT_BASE = """
            SELECT treatment_id, name, description, base_cost,
                   duration_minutes, is_active
            FROM treatments
            """;

    public Optional<Treatment> findById(Long treatmentId) {
        try (Connection conn = DBConnectionManager.getInstance().getConnection()) {
            return findById(conn, treatmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up treatment " + treatmentId, e);
        }
    }

    public Optional<Treatment> findById(Connection conn, Long treatmentId) {
        String sql = SELECT_BASE + " WHERE treatment_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, treatmentId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up treatment " + treatmentId, e);
        }
    }

    public List<Treatment> findAllActive() {
        String sql = SELECT_BASE + " WHERE is_active = TRUE ORDER BY name";
        List<Treatment> results = new ArrayList<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to list treatments", e);
        }
    }

    /** Price changes are restricted to the administrator role. */
    public void updateBaseCost(Long treatmentId, java.math.BigDecimal newCost) {
        String sql = "UPDATE treatments SET base_cost = ? WHERE treatment_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBigDecimal(1, newCost);
            ps.setLong(2, treatmentId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to update treatment price", e);
        }
    }

    private Treatment mapRow(ResultSet rs) throws SQLException {
        Treatment t = new Treatment();
        t.setTreatmentId(rs.getLong("treatment_id"));
        t.setName(rs.getString("name"));
        t.setDescription(rs.getString("description"));
        // getBigDecimal, never getDouble — see Bill for the reasoning.
        t.setBaseCost(rs.getBigDecimal("base_cost"));
        t.setDurationMinutes(rs.getInt("duration_minutes"));
        t.setActive(rs.getBoolean("is_active"));
        return t;
    }
}
