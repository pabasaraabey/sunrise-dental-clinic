package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.DentistDao;
import lk.sdcms.dao.TreatmentDao;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dentists and treatments, for populating the booking form's dropdowns.
 *
 * <p>Served in one call rather than two so the booking page makes a single
 * round trip on load.
 */
@WebServlet(name = "ReferenceDataServlet", urlPatterns = "/api/reference")
public class ReferenceDataServlet extends HttpServlet {

    private DentistDao   dentistDao;
    private TreatmentDao treatmentDao;

    @Override
    public void init() {
        dentistDao   = new DentistDao();
        treatmentDao = new TreatmentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dentists", dentistDao.findAllActive().stream()
                    .map(d -> Map.of(
                            "dentistId",      d.getDentistId(),
                            "name",           d.getFullName(),
                            "specialization", d.getSpecialization(),
                            "availableFrom",  d.getAvailableFrom().toString(),
                            "availableTo",    d.getAvailableTo().toString()))
                    .toList());

            payload.put("treatments", treatmentDao.findAllActive().stream()
                    .map(t -> Map.of(
                            "treatmentId",     t.getTreatmentId(),
                            "name",            t.getName(),
                            "baseCost",        t.getBaseCost(),
                            "durationMinutes", t.getDurationMinutes()))
                    .toList());
            // Map.of rejects null values, so every field mapped above must be
            // non-null in the database. Both are NOT NULL columns.

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK, payload);

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load reference data", request.getRequestURI());
        }
    }
}
