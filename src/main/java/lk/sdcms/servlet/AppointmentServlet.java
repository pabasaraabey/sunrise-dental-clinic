package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.*;
import lk.sdcms.dto.AppointmentDto;
import lk.sdcms.dto.AppointmentRequest;
import lk.sdcms.exception.*;
import lk.sdcms.filter.AuthFilter;
import lk.sdcms.model.Appointment;
import lk.sdcms.model.User;
import lk.sdcms.pattern.AuditLogObserver;
import lk.sdcms.pattern.ConfirmationObserver;
import lk.sdcms.service.AppointmentService;
import lk.sdcms.util.HttpResponses;
import lk.sdcms.util.JsonMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Booking and retrieving appointments.
 *
 * <p>Thin: parse, delegate to one service method, serialise, set a status code.
 * The status codes are chosen deliberately — see the catch blocks.
 */
@WebServlet(name = "AppointmentServlet", urlPatterns = "/api/appointments")
public class AppointmentServlet extends HttpServlet {

    private AppointmentService appointmentService;

    @Override
    public void init() {
        appointmentService = new AppointmentService(
                new AppointmentDao(), new PatientDao(),
                new DentistDao(), new TreatmentDao());

        appointmentService.registerObserver(new AuditLogObserver());
        appointmentService.registerObserver(new ConfirmationObserver());
    }

    /** Search by appointment number, or list a day's appointments. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String appointmentNo = request.getParameter("no");
        String dateParam     = request.getParameter("date");

        try {
            if (appointmentNo != null && !appointmentNo.isBlank()) {
                Appointment found = appointmentService.findByNo(appointmentNo);
                HttpResponses.writeJson(response, HttpServletResponse.SC_OK,
                        AppointmentDto.from(found));
                return;
            }

            LocalDate date = (dateParam == null || dateParam.isBlank())
                    ? LocalDate.now()
                    : LocalDate.parse(dateParam);

            List<AppointmentDto> results = appointmentService.findByDate(date)
                    .stream().map(AppointmentDto::from).toList();

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK, results);

        } catch (ResourceNotFoundException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(), request.getRequestURI());

        } catch (java.time.format.DateTimeParseException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Date must be in YYYY-MM-DD format", request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to retrieve appointments", request.getRequestURI());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User currentUser = AuthFilter.currentUser(request);

        try {
            AppointmentRequest body = JsonMapper.fromJson(
                    new String(request.getInputStream().readAllBytes()),
                    AppointmentRequest.class);

            Appointment booked = appointmentService.book(body, currentUser.getUserId());

            response.setHeader("Location",
                    request.getRequestURI() + "?no=" + booked.getAppointmentNo());
            HttpResponses.writeJson(response, HttpServletResponse.SC_CREATED,
                    AppointmentDto.from(booked));

        } catch (SlotUnavailableException e) {
            // 409 Conflict, not 400. The request is perfectly well formed; it
            // conflicts with the current state of the resource, which is
            // exactly what 409 denotes.
            HttpResponses.writeError(response, HttpServletResponse.SC_CONFLICT,
                    "That slot is already booked. Please choose another time.",
                    request.getRequestURI());

        } catch (ValidationException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(), request.getRequestURI());

        } catch (ResourceNotFoundException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(), request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Booking failed. Please try again.", request.getRequestURI());
        }
    }
}
