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


    /** Every treatment including retired ones — used by the management screen. */
    public List<Treatment> findAll() {
        String sql = SELECT_BASE + " ORDER BY is_active DESC, name";
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

    public Treatment insert(Treatment treatment) {
        String sql = """
                INSERT INTO treatments
                    (name, description, base_cost, duration_minutes, is_active)
                VALUES (?, ?, ?, ?, TRUE)
                """;

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, treatment.getName());
            ps.setString(2, treatment.getDescription());
            ps.setBigDecimal(3, treatment.getBaseCost());
            ps.setInt(4, treatment.getDurationMinutes());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    treatment.setTreatmentId(keys.getLong(1));
                }
            }
            return treatment;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DataAccessException(
                    "A treatment named '" + treatment.getName() + "' already exists", e);

        } catch (SQLException e) {
            throw new DataAccessException("Failed to add treatment", e);
        }
    }

    /**
     * Amends a treatment.
     *
     * <p>Changing a price affects future bills only. Bills already issued store
     * the amounts that were charged at the time, so revising a price here never
     * rewrites the clinic's accounting history.
     */
    public void update(Treatment treatment) {
        String sql = """
                UPDATE treatments SET
                    name = ?, description = ?, base_cost = ?, duration_minutes = ?
                WHERE treatment_id = ?
                """;

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, treatment.getName());
            ps.setString(2, treatment.getDescription());
            ps.setBigDecimal(3, treatment.getBaseCost());
            ps.setInt(4, treatment.getDurationMinutes());
            ps.setLong(5, treatment.getTreatmentId());
            ps.executeUpdate();

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DataAccessException(
                    "Another treatment already uses the name '"
                    + treatment.getName() + "'", e);

        } catch (SQLException e) {
            throw new DataAccessException("Failed to update treatment", e);
        }
    }

    /**
     * Retires or reinstates a treatment. Never deletes — historical
     * appointments reference this row and removing it would invalidate them.
     */
    public void setActive(Long treatmentId, boolean active) {
        String sql = "UPDATE treatments SET is_active = ? WHERE treatment_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, active);
            ps.setLong(2, treatmentId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to change treatment status", e);
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
