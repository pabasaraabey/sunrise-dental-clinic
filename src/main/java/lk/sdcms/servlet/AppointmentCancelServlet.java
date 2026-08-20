package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.*;
import lk.sdcms.dto.AppointmentDto;
import lk.sdcms.exception.ResourceNotFoundException;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.model.Appointment;
import lk.sdcms.service.AppointmentService;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;

@WebServlet(name = "AppointmentCancelServlet", urlPatterns = "/api/appointments/cancel")
public class AppointmentCancelServlet extends HttpServlet {

    private AppointmentService appointmentService;

    @Override
    public void init() {
        appointmentService = new AppointmentService(
                new AppointmentDao(), new PatientDao(),
                new DentistDao(), new TreatmentDao());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String appointmentNo = request.getParameter("no");

        if (appointmentNo == null || appointmentNo.isBlank()) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Appointment number is required", request.getRequestURI());
            return;
        }

        try {
            Appointment cancelled = appointmentService.cancel(appointmentNo);
            HttpResponses.writeJson(response, HttpServletResponse.SC_OK,
                    AppointmentDto.from(cancelled));

        } catch (ResourceNotFoundException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(), request.getRequestURI());

        } catch (ValidationException e) {
            // 422, not 400. The request is well formed and the resource exists;
            // a business rule forbids this particular state transition. The
            // client needs that distinction to show the right message.
            HttpResponses.writeError(response, 422,
                    e.getMessage(), request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Cancellation failed", request.getRequestURI());
        }
    }
}
