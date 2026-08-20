package lk.sdcms.model;

/**
 * Appointment lifecycle. Cancelled appointments are retained rather than
 * deleted — the no-show and cancellation report depends on that history,
 * as would any subsequent billing dispute.
 */
public enum AppointmentStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
