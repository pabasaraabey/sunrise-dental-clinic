package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.AppointmentDao;
import lk.sdcms.dao.BillDao;
import lk.sdcms.exception.ResourceNotFoundException;
import lk.sdcms.model.Appointment;
import lk.sdcms.model.Bill;
import lk.sdcms.service.BillingService;
import lk.sdcms.util.HttpResponses;
import lk.sdcms.util.PdfReceiptGenerator;

import java.io.IOException;

/** Streams the printable receipt. */
@WebServlet(name = "BillPdfServlet", urlPatterns = "/api/bills/pdf")
public class BillPdfServlet extends HttpServlet {

    private BillingService billingService;
    private AppointmentDao appointmentDao;

    @Override
    public void init() {
        appointmentDao = new AppointmentDao();
        billingService = new BillingService(new BillDao(), appointmentDao);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String billId = request.getParameter("id");

        if (billId == null || billId.isBlank()) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Bill id is required", request.getRequestURI());
            return;
        }

        try {
            Bill bill = billingService.findById(billId);

            Appointment appointment = appointmentDao.findByNo(bill.getAppointmentNo())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Appointment", bill.getAppointmentNo()));

            byte[] pdf = PdfReceiptGenerator.generate(bill, appointment,
                    billingService.selectStrategy(appointment).description());

            response.setContentType("application/pdf");
            response.setContentLength(pdf.length);
            // 'inline' so the browser previews it rather than forcing a download;
            // the receptionist usually wants to print, not save.
            response.setHeader("Content-Disposition",
                    "inline; filename=\"" + billId + ".pdf\"");
            response.getOutputStream().write(pdf);

        } catch (ResourceNotFoundException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                    e.getMessage(), request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to render the receipt", request.getRequestURI());
        }
    }
}
