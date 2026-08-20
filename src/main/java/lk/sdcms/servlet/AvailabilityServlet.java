package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.*;
import lk.sdcms.exception.ResourceNotFoundException;
import lk.sdcms.service.AppointmentService;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Free slots for a dentist on a date.
 *
 * <p>Offering only free slots is what stops most double bookings before they
 * are attempted. It is a usability measure, not a correctness one — the
 * database constraint remains the guarantee.
 */
@WebServlet(name = "AvailabilityServlet", urlPatterns = "/api/appointments/availability")
public class AvailabilityServlet extends HttpServlet {

    private AppointmentService appointmentService;

    @Override
    public void init() {
        appointmentService = new AppointmentService(
                new AppointmentDao(), new PatientDao(),
                new DentistDao(), new TreatmentDao());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String dentistParam = request.getParameter("dentistId");
        String dateParam    = request.getParameter("date");

        if (dentistParam == null || dateParam == null) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "dentistId and date are both required", request.getRequestURI());
            return;
        }

        try {
            List<LocalTime> slots = appointmentService.availableSlots(
                    Long.parseLong(dentistParam), LocalDate.parse(dateParam));

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK, slots);

        } catch (ResourceNotFoundException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(), request.getRequestURI());

        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "dentistId must be numeric and date must be YYYY-MM-DD",
                    request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to check availability", request.getRequestURI());
        }
    }
}
