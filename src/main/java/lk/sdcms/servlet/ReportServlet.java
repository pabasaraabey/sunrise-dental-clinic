package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.AppointmentDao;
import lk.sdcms.dao.ReportDao;
import lk.sdcms.dto.AppointmentDto;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The clinic's reports.
 *
 * <p>One servlet with a {@code type} parameter rather than six servlets. Each
 * report is a single query and a single response shape; six near-identical
 * classes would repeat the same date-parsing and error handling six times.
 */
@WebServlet(name = "ReportServlet", urlPatterns = "/api/reports")
public class ReportServlet extends HttpServlet {

    private ReportDao reportDao;
    private AppointmentDao appointmentDao;

    @Override
    public void init() {
        reportDao = new ReportDao();
        appointmentDao = new AppointmentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String type = request.getParameter("type");

        try {
            LocalDate from = parseDate(request.getParameter("from"),
                    LocalDate.now().withDayOfMonth(1));
            LocalDate to = parseDate(request.getParameter("to"), LocalDate.now());

            if (from.isAfter(to)) {
                HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "The start date must not be after the end date",
                        request.getRequestURI());
                return;
            }

            // Checked before the switch rather than inside it: a switch
            // expression must yield a value on every path, so returning from
            // within one is a compile error.
            String patientIdParam = request.getParameter("patientId");

            if ("patient-history".equals(type)
                    && (patientIdParam == null || patientIdParam.isBlank())) {
                HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "patientId is required for the visit history report",
                        request.getRequestURI());
                return;
            }

            Object payload = switch (type == null ? "" : type) {

                case "summary" -> reportDao.dashboardSummary(
                        parseDate(request.getParameter("date"), LocalDate.now()));

                case "schedule" -> {
                    LocalDate day = parseDate(request.getParameter("date"), LocalDate.now());
                    List<AppointmentDto> rows = appointmentDao.findByDate(day)
                            .stream().map(AppointmentDto::from).toList();
                    yield wrap("Daily appointment schedule", day.toString(), rows);
                }

                case "workload" -> wrap("Dentist workload",
                        from + " to " + to, reportDao.dentistWorkload(from, to));

                case "revenue" -> wrap("Revenue by day",
                        from + " to " + to, reportDao.revenueByDay(from, to));

                case "revenue-monthly" -> wrap("Revenue by month",
                        from + " to " + to, reportDao.revenueByMonth(from, to));

                case "treatments" -> wrap("Treatment popularity",
                        from + " to " + to, reportDao.treatmentPopularity(from, to));

                case "patient-history" -> {
                    List<AppointmentDto> rows =
                            appointmentDao.findByPatient(Long.valueOf(patientIdParam))
                                    .stream().map(AppointmentDto::from).toList();
                    yield wrap("Patient visit history", "All visits", rows);
                }

                case "no-show" -> wrap("No-shows and cancellations",
                        from + " to " + to, reportDao.noShowAndCancellation(from, to));

                case "trend" -> {
                    String granularity = request.getParameter("granularity");
                    yield wrap("Booking trend",
                            from + " to " + to,
                            reportDao.bookingTrend(from, to, granularity));
                }

                case "patient-activity" -> wrap("Patient activity",
                        "All registered patients", reportDao.patientActivity());

                case "averages" -> reportDao.averages(from, to);

                case "pending" -> {
                    // The receptionist's work queue: today's appointments that
                    // have not yet been closed out either way.
                    List<AppointmentDto> rows = appointmentDao
                            .findByDate(parseDate(request.getParameter("date"),
                                    LocalDate.now()))
                            .stream()
                            .map(AppointmentDto::from)
                            .filter(a -> "SCHEDULED".equals(a.getStatus()))
                            .toList();
                    yield wrap("Pending appointments", "Awaiting outcome", rows);
                }

                default -> null;
            };

            if (payload == null) {
                HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Unknown report type", request.getRequestURI());
                return;
            }

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK, payload);

        } catch (DateTimeParseException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Dates must be in YYYY-MM-DD format", request.getRequestURI());

        } catch (NumberFormatException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "patientId must be numeric", request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "The report could not be produced", request.getRequestURI());
        }
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        return (value == null || value.isBlank()) ? fallback : LocalDate.parse(value);
    }

    /** Carries the title and period alongside the rows so the PDF and the
     *  on-screen table can both label themselves without guessing. */
    private Map<String, Object> wrap(String title, String period, Object rows) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("period", period);
        payload.put("generatedAt", java.time.LocalDateTime.now().toString());
        payload.put("rows", rows);
        return payload;
    }
}
