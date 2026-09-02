package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.UserDao;
import lk.sdcms.dto.UserDto;
import lk.sdcms.exception.DataAccessException;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.filter.AuthFilter;
import lk.sdcms.model.*;
import lk.sdcms.service.AuthenticationService;
import lk.sdcms.util.HttpResponses;
import lk.sdcms.util.JsonMapper;

import java.io.IOException;
import java.util.Map;

/**
 * Staff account management — the one thing reserved for the administrator.
 *
 * <p>AuthFilter already blocks non-administrators from reaching this servlet,
 * so nothing here re-checks the role. The restriction exists in one place
 * rather than two, which means it cannot drift out of step with itself.
 */
@WebServlet(name = "UserServlet", urlPatterns = "/api/users")
public class UserServlet extends HttpServlet {

    private UserDao userDao;
    private AuthenticationService authService;

    @Override
    public void init() {
        userDao = new UserDao();
        authService = new AuthenticationService(userDao);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            // DTOs, never the entities — User carries passwordHash and the
            // lockout fields, none of which belong on the wire.
            HttpResponses.writeJson(response, HttpServletResponse.SC_OK,
                    userDao.findAll().stream().map(UserDto::from).toList());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load staff accounts", request.getRequestURI());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String action = request.getParameter("action");
        User currentUser = AuthFilter.currentUser(request);

        try {
            if ("disable".equals(action) || "enable".equals(action)) {
                Long id = Long.valueOf(request.getParameter("id"));

                // Disabling your own account would lock you out of the only
                // screen that could re-enable it.
                if (id.equals(currentUser.getUserId()) && "disable".equals(action)) {
                    throw new ValidationException("You cannot disable your own account");
                }

                userDao.setActive(id, "enable".equals(action));
                HttpResponses.writeMessage(response, HttpServletResponse.SC_OK,
                        "enable".equals(action) ? "Account enabled" : "Account disabled");
                return;
            }

            if ("resetPassword".equals(action)) {
                Long id = Long.valueOf(request.getParameter("id"));
                String password = request.getParameter("password");

                authService.requireCompliantPassword(password);
                userDao.updatePassword(id, authService.hashPassword(password));

                HttpResponses.writeMessage(response, HttpServletResponse.SC_OK,
                        "Password changed and any lockout cleared");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> body = JsonMapper.fromJson(
                    new String(request.getInputStream().readAllBytes()), Map.class);

            createAccount(body, response, request);

        } catch (ValidationException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(), request.getRequestURI());

        } catch (DataAccessException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_CONFLICT,
                    e.getMessage(), request.getRequestURI());

        } catch (NumberFormatException e) {
            HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "User id must be numeric", request.getRequestURI());

        } catch (RuntimeException e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to save the account", request.getRequestURI());
        }
    }

    private void createAccount(Map<String, String> body,
                               HttpServletResponse response,
                               HttpServletRequest request) throws IOException {

        if (body == null) {
            throw new ValidationException("Request body is required");
        }

        String username = body.get("username");
        String fullName = body.get("fullName");
        String password = body.get("password");
        String roleName = body.get("role");

        if (username == null || username.isBlank()) {
            throw new ValidationException("Username is required");
        }
        if (!username.matches("^[a-zA-Z0-9._-]{3,50}$")) {
            throw new ValidationException(
                    "Username must be 3 to 50 characters, letters, digits, dot, dash or underscore");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("Full name is required");
        }
        if (roleName == null || roleName.isBlank()) {
            throw new ValidationException("Role is required");
        }

        // Throws with a specific message if the password is too weak.
        authService.requireCompliantPassword(password);

        Role role;
        try {
            role = Role.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Role must be ADMINISTRATOR or RECEPTIONIST");
        }

        User user = (role == Role.ADMINISTRATOR)
                ? new Administrator()
                : new Receptionist();

        user.setUsername(username.trim());
        user.setFullName(fullName.trim());

        // Hashing happens in the service; the DAO only ever sees the hash.
        User saved = userDao.insert(user, authService.hashPassword(password));

        HttpResponses.writeJson(response, HttpServletResponse.SC_CREATED,
                UserDto.from(saved));
    }
}
