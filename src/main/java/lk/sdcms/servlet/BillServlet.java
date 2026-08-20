package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.AppointmentDao;
import lk.sdcms.dao.BillDao;
import lk.sdcms.dto.BillDto;
import lk.sdcms.exception.ResourceNotFoundException;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.filter.AuthFilter;
import lk.sdcms.model.Appointment;
import lk.sdcms.model.Bill;
import lk.sdcms.model.User;
import lk.sdcms.service.BillingService;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;

@WebServlet(name = "BillServlet", urlPatterns = "/api/bills")
public class BillServlet extends HttpServlet {

    private BillingService billingService;
    private AppointmentDao appointmentDao;

    @Override
    public void init() {
        appointmentDao = new AppointmentDao();
        billingService = new BillingService(new BillDao(), appointmentDao);
    }

    /** Retrieves an existing bill by appointment number. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String appointmentNo = request.getParameter("appointmentNo");

        if (appointmentNo == null || appointmentNo.isBlank()) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "appointmentNo is required", request.getRequestURI());
            return;
        }

        try {
            Bill bill = billingService.findByAppointmentNo(appointmentNo);
            Appointment appointment = appointmentDao.findByNo(appointmentNo)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Appointment", appointmentNo));

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK,
                    BillDto.from(bill,
                            billingService.selectStrategy(appointment).description()));

        } catch (ResourceNotFoundException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(), request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to retrieve the bill", request.getRequestURI());
        }
    }

    /** Generates a new bill for an appointment. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String appointmentNo = request.getParameter("appointmentNo");
        User currentUser = AuthFilter.currentUser(request);

        if (appointmentNo == null || appointmentNo.isBlank()) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "appointmentNo is required", request.getRequestURI());
            return;
        }

        try {
            Bill bill = billingService.generateBill(appointmentNo, currentUser.getUserId());

            Appointment appointment = appointmentDao.findByNo(appointmentNo)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Appointment", appointmentNo));

            HttpResponses.writeJson(response, HttpServletResponse.SC_CREATED,
                    BillDto.from(bill,
                            billingService.selectStrategy(appointment).description()));

        } catch (ResourceNotFoundException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(), request.getRequestURI());

        } catch (ValidationException e) {
            // 409: the appointment exists and the request is valid, but a bill
            // already exists for it — a conflict with resource state.
            HttpResponses.writeError(response, HttpServletResponse.SC_CONFLICT,
                    e.getMessage(), request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Bill generation failed", request.getRequestURI());
        }
    }
}
