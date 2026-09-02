package lk.sdcms.model;

/**
 * Login roles.
 *
 * <p>The clinic has a handful of staff, not a finance department. Requiring a
 * separate administrator for routine work would leave reception unable to
 * operate whenever the owner is out, so the receptionist role carries the
 * operational duties — patients, dentists, treatments, prices, appointments
 * and billing.
 *
 * <p>The administrator role exists to create and deactivate staff accounts.
 * Because this deliberately forgoes separation of duties between price
 * maintenance and billing, audit_log is the compensating control: every price
 * change and every bill is attributed to a named user and timestamped.
 *
 * <p>Dentists are records rather than users — reception maintains them so their
 * details flow into appointments and receipts, but they do not log in.
 */
public enum Role {
    ADMINISTRATOR,
    RECEPTIONIST
}
