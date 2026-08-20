package lk.sdcms.model;

/**
 * System roles. Separation of duties: a receptionist who could also change
 * treatment prices would be able to under-charge and conceal it, so price
 * maintenance is restricted to the administrator.
 */
public enum Role {
    ADMINISTRATOR,
    RECEPTIONIST,
    DENTIST
}
