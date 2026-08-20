package lk.sdcms.service;

import lk.sdcms.dao.UserDao;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.model.Receptionist;
import lk.sdcms.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Authentication behaviour, tested with the DAO mocked.
 *
 * <p>No database is involved. That is possible only because
 * AuthenticationService takes its DAO through the constructor — had it created
 * one internally, every test here would need a running MySQL instance and
 * would be testing two things at once.
 */
class AuthenticationServiceTest {

    private static final String PLAINTEXT = "Recep@123";

    private UserDao userDao;
    private AuthenticationService service;
    private User existingUser;

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        service = new AuthenticationService(userDao);

        existingUser = new Receptionist();
        existingUser.setUserId(2L);
        existingUser.setUsername("reception");
        existingUser.setFullName("Kamala Silva");
        existingUser.setPasswordHash(BCrypt.hashpw(PLAINTEXT, BCrypt.gensalt(10)));
        existingUser.setActive(true);
        existingUser.setFailedAttempts(0);
    }

    @Test
    @DisplayName("Correct credentials return the authenticated user")
    void validLoginSucceeds() {
        when(userDao.findByUsername("reception")).thenReturn(Optional.of(existingUser));

        User result = service.authenticate("reception", PLAINTEXT);

        assertEquals("reception", result.getUsername());
        verify(userDao).resetFailedAttempts(2L);
    }

    @Test
    @DisplayName("A wrong password is rejected and the failure recorded")
    void wrongPasswordIsRejected() {
        when(userDao.findByUsername("reception")).thenReturn(Optional.of(existingUser));

        assertThrows(ValidationException.class,
                () -> service.authenticate("reception", "WrongPass@1"));

        verify(userDao).recordFailedAttempt(eq(2L), eq(1), isNull());
    }

    @Test
    @DisplayName("An unknown username gives the same message as a wrong password")
    void unknownUserDoesNotLeakExistence() {
        when(userDao.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userDao.findByUsername("reception")).thenReturn(Optional.of(existingUser));

        String unknownUserMessage = assertThrows(ValidationException.class,
                () -> service.authenticate("ghost", "AnyPass@1")).getMessage();

        String wrongPasswordMessage = assertThrows(ValidationException.class,
                () -> service.authenticate("reception", "WrongPass@1")).getMessage();

        // Identical messages, so an attacker cannot enumerate valid usernames.
        assertEquals(unknownUserMessage, wrongPasswordMessage);
    }

    @Test
    @DisplayName("The third consecutive failure locks the account")
    void thirdFailureAppliesLockout() {
        existingUser.setFailedAttempts(2);
        when(userDao.findByUsername("reception")).thenReturn(Optional.of(existingUser));

        assertThrows(ValidationException.class,
                () -> service.authenticate("reception", "WrongPass@1"));

        // Third attempt, so a lock timestamp must be supplied rather than null.
        verify(userDao).recordFailedAttempt(eq(2L), eq(3), notNull());
    }

    @Test
    @DisplayName("A locked account refuses even the correct password")
    void lockedAccountRefusesCorrectPassword() {
        existingUser.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userDao.findByUsername("reception")).thenReturn(Optional.of(existingUser));

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.authenticate("reception", PLAINTEXT));

        assertTrue(thrown.getMessage().toLowerCase().contains("locked"));
    }

    @Test
    @DisplayName("An expired lock no longer blocks login")
    void expiredLockReleasesItself() {
        existingUser.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(userDao.findByUsername("reception")).thenReturn(Optional.of(existingUser));

        assertDoesNotThrow(() -> service.authenticate("reception", PLAINTEXT));
    }

    @Test
    @DisplayName("A deactivated account cannot log in")
    void deactivatedAccountIsRefused() {
        existingUser.setActive(false);
        when(userDao.findByUsername("reception")).thenReturn(Optional.of(existingUser));

        assertThrows(ValidationException.class,
                () -> service.authenticate("reception", PLAINTEXT));
    }

    @Test
    @DisplayName("Blank credentials are rejected before any lookup")
    void blankCredentialsRejectedEarly() {
        assertThrows(ValidationException.class, () -> service.authenticate("", ""));
        verifyNoInteractions(userDao);
    }

    @Test
    @DisplayName("Password policy requires length, case, digit and symbol")
    void passwordPolicyIsEnforced() {
        assertThrows(ValidationException.class, () -> service.hashPassword("short1!"));
        assertThrows(ValidationException.class, () -> service.hashPassword("nouppercase1!"));
        assertThrows(ValidationException.class, () -> service.hashPassword("NoDigits!!"));
        assertThrows(ValidationException.class, () -> service.hashPassword("NoSymbol123"));

        assertDoesNotThrow(() -> service.hashPassword("Valid@123"));
    }

    @Test
    @DisplayName("Hashing produces a different hash each time but still verifies")
    void hashesAreSalted() {
        String first  = service.hashPassword("Valid@123");
        String second = service.hashPassword("Valid@123");

        // Different salts, so identical passwords do not produce identical
        // hashes — an attacker cannot spot shared passwords across accounts.
        assertNotEquals(first, second);
        assertTrue(service.verifyPassword("Valid@123", first));
        assertTrue(service.verifyPassword("Valid@123", second));
    }

    @Test
    @DisplayName("A malformed stored hash fails the login rather than crashing")
    void malformedHashDoesNotCrash() {
        assertFalse(service.verifyPassword("anything", "not-a-bcrypt-hash"));
    }
}
