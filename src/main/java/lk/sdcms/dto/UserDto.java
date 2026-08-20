package lk.sdcms.dto;

import lk.sdcms.model.User;

/**
 * What the client is told about the logged-in user.
 *
 * <p>A DTO rather than the User entity, because User carries passwordHash and
 * the lockout fields. Serialising the entity directly would put a password
 * hash on the wire — a real leak, and an easy one to cause by accident.
 */
public class UserDto {

    private Long   userId;
    private String username;
    private String fullName;
    private String role;

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.userId   = user.getUserId();
        dto.username = user.getUsername();
        dto.fullName = user.getFullName();
        dto.role     = user.getRole().name();
        return dto;
    }

    public Long   getUserId()   { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getRole()     { return role; }
}
