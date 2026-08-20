package lk.sdcms.pattern;

import lk.sdcms.model.Appointment;

/**
 * Placeholder for the confirmation channel.
 *
 * <p>The clinic has no SMS gateway contract yet, so this logs what would be
 * sent. When a gateway is procured, only this class changes — the booking
 * service does not, which is the point of routing notifications through the
 * observer interface rather than calling a gateway directly from the booking
 * method.
 */
public class ConfirmationObserver implements BookingObserver {

    @Override
    public void onAppointmentBooked(Appointment appointment) {
        System.out.printf(
                "[CONFIRMATION] To %s (%s): appointment %s with %s on %s at %s%n",
                appointment.getPatient().getFullName(),
                appointment.getPatient().getContactNo(),
                appointment.getAppointmentNo(),
                appointment.getDentist().getFullName(),
                appointment.getAppointmentDate(),
                appointment.getAppointmentTime());
    }
}
