package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.TreatmentDao;
import lk.sdcms.exception.DataAccessException;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.model.Treatment;
import lk.sdcms.util.HttpResponses;
import lk.sdcms.util.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Managing treatments and their prices.
 *
 * <p>Prices live in the database rather than in Java so reception can revise
 * them without a redeployment. Changing a price affects future bills only —
 * issued bills store the amounts actually charged, so history is never
 * rewritten.
 */
@WebServlet(name = "TreatmentServlet", urlPatterns = "/api/treatments")
public class TreatmentServlet extends HttpServlet {

    private TreatmentDao treatmentDao;

    @Override
    public void init() {
        treatmentDao = new TreatmentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        boolean activeOnly = "true".equals(request.getParameter("activeOnly"));

        try {
            List<Treatment> treatments = activeOnly
                    ? treatmentDao.findAllActive()
                    : treatmentDao.findAll();

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK, treatments);

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load treatments", request.getRequestURI());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String action = request.getParameter("action");

        try {
            if ("retire".equals(action) || "reinstate".equals(action)) {
                Long id = Long.valueOf(request.getParameter("id"));
                treatmentDao.setActive(id, "reinstate".equals(action));
                HttpResponses.writeMessage(response, HttpServletResponse.SC_OK,
                        "reinstate".equals(action)
                                ? "Treatment reinstated"
                                : "Treatment retired. Past bills are unaffected.");
                return;
            }

            Treatment treatment = JsonMapper.fromJson(
                    new String(request.getInputStream().readAllBytes()), Treatment.class);

            validate(treatment);

            if (treatment.getTreatmentId() == null) {
                Treatment saved = treatmentDao.insert(treatment);
                HttpResponses.writeJson(response, HttpServletResponse.SC_CREATED, saved);
            } else {
                treatmentDao.update(treatment);
                HttpResponses.writeJson(response, HttpServletResponse.SC_OK, treatment);
            }

        } catch (ValidationException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(), request.getRequestURI());

        } catch (DataAccessException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_CONFLICT,
                    e.getMessage(), request.getRequestURI());

        } catch (NumberFormatException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Treatment id must be numeric", request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to save the treatment", request.getRequestURI());
        }
    }

    private void validate(Treatment t) {
        if (t == null) {
            throw new ValidationException("Request body is required");
        }
        if (t.getName() == null || t.getName().isBlank()) {
            throw new ValidationException("Treatment name is required");
        }
        if (t.getBaseCost() == null) {
            throw new ValidationException("Price is required");
        }
        if (t.getBaseCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Price cannot be negative");
        }
        // Two decimal places, matching DECIMAL(10,2). More would be silently
        // truncated by the database, so it is rejected here instead.
        if (t.getBaseCost().scale() > 2) {
            throw new ValidationException("Price cannot have more than two decimal places");
        }
        if (t.getDurationMinutes() <= 0 || t.getDurationMinutes() > 480) {
            throw new ValidationException("Duration must be between 1 and 480 minutes");
        }
    }
}
