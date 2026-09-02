package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.AppointmentDao;
import lk.sdcms.dto.AppointmentDto;
import lk.sdcms.exception.ResourceNotFoundException;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.model.Appointment;
import lk.sdcms.model.AppointmentStatus;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;

/**
 * Closing out an appointment once the patient has been seen — or has not.
 *
 * <p>This is the receptionist's end-of-visit action. Until it happens the
 * appointment sits in the pending queue on the dashboard, which is how the
 * clinic knows what still needs attention.
 *
 * <p>Only two transitions are permitted, both from SCHEDULED. Marking a
 * cancelled appointment as completed, or completing one twice, would corrupt
 * the workload and no-show reports, so the state machine is enforced here
 * rather than trusting the caller to send something sensible.
 */
@WebServlet(name = "AppointmentStatusServlet", urlPatterns = "/api/appointments/status")
public class AppointmentStatusServlet extends HttpServlet {

    private AppointmentDao appointmentDao;

    @Override
    public void init() {
        appointmentDao = new AppointmentDao();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String appointmentNo = request.getParameter("no");
        String statusParam   = request.getParameter("status");

        if (appointmentNo == null || appointmentNo.isBlank()) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Appointment number is required", request.getRequestURI());
            return;
        }

        try {
            AppointmentStatus target = parseStatus(statusParam);

            Appointment appointment = appointmentDao.findByNo(appointmentNo)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Appointment", appointmentNo));

            requireTransitionAllowed(appointment.getStatus(), target);

            appointmentDao.updateStatus(appointmentNo, target);
            appointment.setStatus(target);

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK,
                    AppointmentDto.from(appointment));

        } catch (ResourceNotFoundException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(), request.getRequestURI());

        } catch (ValidationException e) {
            // 422: the request is well formed and the appointment exists, but
            // the state machine forbids this particular move.
            HttpResponses.writeError(response, 422,
                    e.getMessage(), request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to update the appointment", request.getRequestURI());
        }
    }

    private AppointmentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("A status is required");
        }
        try {
            AppointmentStatus status = AppointmentStatus.valueOf(value.toUpperCase());

            // Cancellation has its own endpoint, which enforces the two-hour
            // cutoff. Allowing it here would bypass that rule.
            if (status == AppointmentStatus.CANCELLED) {
                throw new ValidationException(
                        "Use the cancel action, which applies the two-hour cutoff");
            }
            if (status == AppointmentStatus.SCHEDULED) {
                throw new ValidationException(
                        "An appointment cannot be moved back to scheduled");
            }
            return status;

        } catch (IllegalArgumentException e) {
            throw new ValidationException("Status must be COMPLETED or NO_SHOW");
        }
    }

    private void requireTransitionAllowed(AppointmentStatus current,
                                          AppointmentStatus target) {
        if (current == target) {
            throw new ValidationException(
                    "This appointment is already marked " + current.name().toLowerCase());
        }
        if (current != AppointmentStatus.SCHEDULED) {
            throw new ValidationException(
                    "Only a scheduled appointment can be marked "
                    + target.name().toLowerCase().replace('_', ' ')
                    + ". This one is " + current.name().toLowerCase().replace('_', ' ') + ".");
        }
    }
}
