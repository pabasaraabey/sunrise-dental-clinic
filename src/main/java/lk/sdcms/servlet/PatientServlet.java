package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.AppointmentDao;
import lk.sdcms.dao.PatientDao;
import lk.sdcms.dto.AppointmentDto;
import lk.sdcms.model.Patient;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Browsing registered patients and their visit history.
 *
 * <p>Patients are created during booking rather than through a separate
 * registration step, which is how reception actually works — nobody registers
 * a patient who is not there to book something.
 */
@WebServlet(name = "PatientServlet", urlPatterns = "/api/patients")
public class PatientServlet extends HttpServlet {

    private PatientDao patientDao;
    private AppointmentDao appointmentDao;

    @Override
    public void init() {
        patientDao = new PatientDao();
        appointmentDao = new AppointmentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String idParam = request.getParameter("id");

        try {
            if (idParam != null && !idParam.isBlank()) {
                Optional<Patient> found = patientDao.findById(Long.valueOf(idParam));

                if (found.isEmpty()) {
                    HttpResponses.writeError(response, HttpServletResponse.SC_NOT_FOUND,
                            "No patient with that id", request.getRequestURI());
                    return;
                }

                Patient patient = found.get();
                List<AppointmentDto> visits =
                        appointmentDao.findByPatient(patient.getPatientId())
                                .stream().map(AppointmentDto::from).toList();

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("patient", toMap(patient));
                payload.put("visits", visits);

                HttpResponses.writeJson(response, HttpServletResponse.SC_OK, payload);
                return;
            }

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK,
                    patientDao.findAll().stream().map(this::toMap).toList());

        } catch (NumberFormatException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Patient id must be numeric", request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load patients", request.getRequestURI());
        }
    }

    /** Age is derived here rather than stored, so it is never stale. */
    private Map<String, Object> toMap(Patient p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patientId", p.getPatientId());
        m.put("fullName", p.getFullName());
        m.put("contactNo", p.getContactNo());
        m.put("address", p.getAddress());
        m.put("email", p.getEmail());
        m.put("dateOfBirth", p.getDateOfBirth());
        m.put("gender", p.getGender() == null ? null : p.getGender().name());
        m.put("age", p.getAge());
        m.put("seniorCitizen", p.isSeniorCitizen());
        m.put("registeredDate", p.getRegisteredDate());
        return m;
    }
}
