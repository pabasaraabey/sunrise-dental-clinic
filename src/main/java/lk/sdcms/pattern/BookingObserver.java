package lk.sdcms.pattern;

import lk.sdcms.model.Appointment;

/**
 * Observer pattern — notified after an appointment is successfully booked.
 *
 * <p>Without this, every new notification channel would mean editing the
 * booking method itself. The clinic may later want an SMS reminder, an email
 * confirmation, or an entry on a dentist's dashboard; each of those becomes a
 * new implementation of this interface registered with the service, and the
 * booking logic never changes.
 */
public interface BookingObserver {

    void onAppointmentBooked(Appointment appointment);
}
