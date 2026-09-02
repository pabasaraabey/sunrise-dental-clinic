package lk.sdcms.dao;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.model.*;
import lk.sdcms.util.DBConnectionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the users table.
 *
 * <p>Every statement is a {@link PreparedStatement}. Building SQL by string
 * concatenation would expose the application to injection, and unlike an ORM
 * project there is no framework here quietly parameterising queries on our
 * behalf — it has to be done deliberately.
 */
public class UserDao {

    private static final String SELECT_BASE = """
            SELECT user_id, username, password_hash, full_name, role,
                   is_active, failed_attempts, locked_until, created_at
            FROM users
            """;

    public Optional<User> findByUsername(String username) {
        String sql = SELECT_BASE + " WHERE username = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up user " + username, e);
        }
    }

    public Optional<User> findById(Long userId) {
        String sql = SELECT_BASE + " WHERE user_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up user " + userId, e);
        }
    }

    /** Records a failed login and applies a lockout once the limit is reached. */
    public void recordFailedAttempt(Long userId, int newCount, LocalDateTime lockUntil) {
        String sql = "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE user_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, newCount);
            if (lockUntil == null) {
                ps.setNull(2, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(2, Timestamp.valueOf(lockUntil));
            }
            ps.setLong(3, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to record login attempt", e);
        }
    }

    /** Clears the failure counter after a successful login. */
    public void resetFailedAttempts(Long userId) {
        String sql = "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to reset login attempts", e);
        }
    }


    /** All staff accounts, for the administrator's management screen. */
    public List<User> findAll() {
        String sql = SELECT_BASE + " ORDER BY is_active DESC, username";
        List<User> results = new ArrayList<>();

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;

        } catch (SQLException e) {
            throw new DataAccessException("Failed to list staff accounts", e);
        }
    }

    /**
     * Creates a staff account.
     *
     * <p>The caller supplies an already-hashed password. Hashing belongs in
     * AuthenticationService, and passing plaintext into the DAO would invite
     * someone to store it unhashed by mistake.
     */
    public User insert(User user, String passwordHash) {
        String sql = """
                INSERT INTO users (username, password_hash, full_name, role, is_active)
                VALUES (?, ?, ?, ?, TRUE)
                """;

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, passwordHash);
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setUserId(keys.getLong(1));
                }
            }
            return user;

        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DataAccessException(
                    "The username '" + user.getUsername() + "' is already taken", e);

        } catch (SQLException e) {
            throw new DataAccessException("Failed to create staff account", e);
        }
    }

    /**
     * Enables or disables an account. Never deletes — appointments and bills
     * record who created them, and removing the user would orphan that trail.
     */
    public void setActive(Long userId, boolean active) {
        String sql = "UPDATE users SET is_active = ? WHERE user_id = ?";

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, active);
            ps.setLong(2, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to change account status", e);
        }
    }

    public void updatePassword(Long userId, String passwordHash) {
        String sql = """
                UPDATE users SET password_hash = ?, failed_attempts = 0,
                                 locked_until = NULL
                WHERE user_id = ?
                """;

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Failed to change password", e);
        }
    }

    /**
     * Instantiates the concrete subclass matching the stored role. This is
     * where the abstract User hierarchy is reconstituted from a flat table —
     * the role column drives which type is created.
     */
    private User mapRow(ResultSet rs) throws SQLException {
        Role role = Role.valueOf(rs.getString("role"));

        // Dentists are records rather than users, so only two roles map here.
        User user = switch (role) {
            case ADMINISTRATOR -> new Administrator();
            case RECEPTIONIST  -> new Receptionist();
        };

        user.setUserId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setRole(role);
        user.setActive(rs.getBoolean("is_active"));
        user.setFailedAttempts(rs.getInt("failed_attempts"));

        Timestamp locked = rs.getTimestamp("locked_until");
        if (locked != null) {
            user.setLockedUntil(locked.toLocalDateTime());
        }
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            user.setCreatedAt(created.toLocalDateTime());
        }
        return user;
    }
}
