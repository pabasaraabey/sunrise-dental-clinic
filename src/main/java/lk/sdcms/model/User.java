package lk.sdcms.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Base type for everyone who can log in.
 *
 * <p>Declared abstract with {@link #getPermissions()} left to subclasses so
 * that authentication code can operate on the abstract type without knowing
 * which role it is holding. Adding a role later means adding one subclass and
 * changing no existing code.
 */
public abstract class User {

    private Long          userId;
    private String        username;
    private String        passwordHash;
    private String        fullName;
    private Role          role;
    private boolean       active = true;
    private int           failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;

    protected User() {
    }

    protected User(Long userId, String username, String passwordHash,
                   String fullName, Role role) {
        this.userId       = userId;
        this.username     = username;
        this.passwordHash = passwordHash;
        this.fullName     = fullName;
        this.role         = role;
    }

    /** Each role answers this differently — resolved polymorphically. */
    public abstract Set<String> getPermissions();

    /**
     * True while a lockout imposed after repeated failed logins is still in
     * force. Compared against the current time rather than stored as a flag,
     * so the lock expires on its own without a scheduled job.
     */
    public boolean isCurrentlyLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public Long getUserId()                 { return userId; }
    public void setUserId(Long userId)      { this.userId = userId; }

    public String getUsername()             { return username; }
    public void setUsername(String u)       { this.username = u; }

    public String getPasswordHash()         { return passwordHash; }
    public void setPasswordHash(String h)   { this.passwordHash = h; }

    public String getFullName()             { return fullName; }
    public void setFullName(String n)       { this.fullName = n; }

    public Role getRole()                   { return role; }
    public void setRole(Role role)          { this.role = role; }

    public boolean isActive()               { return active; }
    public void setActive(boolean a)        { this.active = a; }

    public int getFailedAttempts()          { return failedAttempts; }
    public void setFailedAttempts(int f)    { this.failedAttempts = f; }

    public LocalDateTime getLockedUntil()          { return lockedUntil; }
    public void setLockedUntil(LocalDateTime t)    { this.lockedUntil = t; }

    public LocalDateTime getCreatedAt()            { return createdAt; }
    public void setCreatedAt(LocalDateTime t)      { this.createdAt = t; }
}
