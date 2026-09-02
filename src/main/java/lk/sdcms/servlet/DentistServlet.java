package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.DentistDao;
import lk.sdcms.exception.DataAccessException;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.model.Dentist;
import lk.sdcms.util.HttpResponses;
import lk.sdcms.util.JsonMapper;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;

/**
 * Managing the clinic's dentists.
 *
 * <p>GET lists them, POST adds or amends one, and the retire action toggles
 * availability. There is no delete: appointments reference dentists, and
 * removing one would orphan history.
 */
@WebServlet(name = "DentistServlet", urlPatterns = "/api/dentists")
public class DentistServlet extends HttpServlet {

    private DentistDao dentistDao;

    @Override
    public void init() {
        dentistDao = new DentistDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        boolean activeOnly = "true".equals(request.getParameter("activeOnly"));

        try {
            List<Dentist> dentists = activeOnly
                    ? dentistDao.findAllActive()
                    : dentistDao.findAll();

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK, dentists);

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load dentists", request.getRequestURI());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String action = request.getParameter("action");

        try {
            if ("retire".equals(action) || "reinstate".equals(action)) {
                Long id = Long.valueOf(request.getParameter("id"));
                dentistDao.setActive(id, "reinstate".equals(action));
                HttpResponses.writeMessage(response, HttpServletResponse.SC_OK,
                        "reinstate".equals(action)
                                ? "Dentist reinstated"
                                : "Dentist retired. Existing appointments are unaffected.");
                return;
            }

            Dentist dentist = JsonMapper.fromJson(
                    new String(request.getInputStream().readAllBytes()), Dentist.class);

            validate(dentist);

            if (dentist.getDentistId() == null) {
                Dentist saved = dentistDao.insert(dentist);
                HttpResponses.writeJson(response, HttpServletResponse.SC_CREATED, saved);
            } else {
                dentistDao.update(dentist);
                HttpResponses.writeJson(response, HttpServletResponse.SC_OK, dentist);
            }

        } catch (ValidationException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(), request.getRequestURI());

        } catch (DataAccessException e) {
            // Duplicate licence number is a conflict with existing data, not a
            // malformed request.
            HttpResponses.writeError(response, HttpServletResponse.SC_CONFLICT,
                    e.getMessage(), request.getRequestURI());

        } catch (NumberFormatException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Dentist id must be numeric", request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to save the dentist", request.getRequestURI());
        }
    }

    private void validate(Dentist d) {
        if (d == null) {
            throw new ValidationException("Request body is required");
        }
        if (d.getFullName() == null || d.getFullName().isBlank()) {
            throw new ValidationException("Dentist name is required");
        }
        if (d.getLicenseNo() == null || d.getLicenseNo().isBlank()) {
            throw new ValidationException("Licence number is required");
        }
        if (d.getSpecialization() == null || d.getSpecialization().isBlank()) {
            throw new ValidationException("Specialisation is required");
        }
        if (d.getContactNo() != null && !d.getContactNo().isBlank()
                && !d.getContactNo().matches("^0\\d{9}$")) {
            throw new ValidationException(
                    "Contact number must be 10 digits beginning with 0");
        }

        LocalTime from = d.getAvailableFrom();
        LocalTime to   = d.getAvailableTo();

        if (from == null || to == null) {
            throw new ValidationException("Working hours are required");
        }
        if (!from.isBefore(to)) {
            throw new ValidationException("The start of the working day must be before the end");
        }
        // Hours outside clinic opening would produce slots nobody can attend.
        if (from.isBefore(LocalTime.of(8, 0)) || to.isAfter(LocalTime.of(20, 0))) {
            throw new ValidationException("Working hours must fall within 08:00 to 20:00");
        }
    }
}
