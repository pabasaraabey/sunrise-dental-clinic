package lk.sdcms.service;

import lk.sdcms.dao.UserDao;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Credential verification and account lockout.
 *
 * <p>The DAO arrives through the constructor rather than being created inside
 * this class. That is what makes the service testable — a unit test supplies a
 * Mockito mock and the database is removed from the test entirely. Writing
 * {@code new UserDao()} here would tie every test to a live MySQL instance.
 */
public class AuthenticationService {

    /** Consecutive failures tolerated before the account locks. */
    public static final int MAX_FAILED_ATTEMPTS = 3;

    /** How long a locked account stays locked. */
    public static final int LOCKOUT_MINUTES = 15;

    private static final Pattern PASSWORD_POLICY = Pattern.compile(
            "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    private final UserDao userDao;

    public AuthenticationService(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * Verifies credentials and returns the authenticated user.
     *
     * <p>Failure messages are deliberately uniform. Distinguishing "no such
     * user" from "wrong password" would let an attacker enumerate valid
     * usernames one request at a time, so both produce the same message.
     *
     * @throws ValidationException on any authentication failure
     */
    public User authenticate(String username, String password) {
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new ValidationException("Username and password are required");
        }

        Optional<User> found = userDao.findByUsername(username);

        if (found.isEmpty()) {
            // Same message as a bad password — no username enumeration.
            throw new ValidationException("Invalid username or password");
        }

        User user = found.get();

        if (!user.isActive()) {
            throw new ValidationException("This account has been deactivated");
        }

        if (user.isCurrentlyLocked()) {
            throw new ValidationException(
                    "Account locked. Try again after " + LOCKOUT_MINUTES + " minutes.");
        }

        if (!verifyPassword(password, user.getPasswordHash())) {
            registerFailure(user);
            throw new ValidationException("Invalid username or password");
        }

        // Successful login clears the counter so isolated typos do not
        // accumulate towards a lockout over days.
        userDao.resetFailedAttempts(user.getUserId());
        user.setFailedAttempts(0);
        user.setLockedUntil(null);

        return user;
    }

    /**
     * Records a failed attempt and applies a lockout at the threshold.
     * The lockout is time-based rather than a flag an administrator must
     * clear, so it releases itself.
     */
    private void registerFailure(User user) {
        int attempts = user.getFailedAttempts() + 1;

        LocalDateTime lockUntil = (attempts >= MAX_FAILED_ATTEMPTS)
                ? LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES)
                : null;

        userDao.recordFailedAttempt(user.getUserId(), attempts, lockUntil);
    }

    /**
     * BCrypt comparison. The stored value is a hash with an embedded salt —
     * plaintext is never stored, and never logged.
     */
    public boolean verifyPassword(String plaintext, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plaintext, storedHash);
        } catch (IllegalArgumentException e) {
            // Malformed hash in the database — treat as a failed login rather
            // than propagating, so a corrupt row cannot crash the login page.
            return false;
        }
    }

    /**
     * Hashes a new password. Cost 10 is a deliberate trade-off: high enough to
     * make brute-forcing expensive, low enough that login stays responsive on
     * clinic hardware.
     */
    public String hashPassword(String plaintext) {
        requireCompliantPassword(plaintext);
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(10));
    }

    /** Minimum 8 characters, one uppercase, one digit, one special character. */
    public void requireCompliantPassword(String password) {
        if (password == null || !PASSWORD_POLICY.matcher(password).matches()) {
            throw new ValidationException(
                    "Password must be at least 8 characters and include an "
                    + "uppercase letter, a digit and a special character");
        }
    }
}
