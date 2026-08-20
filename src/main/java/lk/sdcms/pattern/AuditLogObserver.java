package lk.sdcms.pattern;

import lk.sdcms.exception.DataAccessException;
import lk.sdcms.model.Appointment;
import lk.sdcms.util.DBConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Writes an audit entry when an appointment is booked.
 *
 * <p>Runs on its own connection rather than joining the booking transaction.
 * That is deliberate: an audit record of an attempted operation is exactly
 * what an audit trail exists to preserve, so it should survive even if the
 * booking is later rolled back.
 */
public class AuditLogObserver implements BookingObserver {

    @Override
    public void onAppointmentBooked(Appointment appointment) {
        String sql = """
                INSERT INTO audit_log (user_id, action, entity_name, entity_id)
                VALUES (?, 'CREATE', 'appointments', ?)
                """;

        try (Connection conn = DBConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, appointment.getCreatedBy());
            ps.setString(2, appointment.getAppointmentNo());
            ps.executeUpdate();

        } catch (SQLException e) {
            // A failed audit write must not fail the booking the patient is
            // standing at the desk for. Wrapped and rethrown would do exactly
            // that, so it is swallowed after being surfaced.
            System.err.println("Audit write failed for "
                    + appointment.getAppointmentNo() + ": " + e.getMessage());
        }
    }
}
