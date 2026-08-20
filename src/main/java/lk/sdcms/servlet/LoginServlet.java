package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dao.UserDao;
import lk.sdcms.dto.LoginRequest;
import lk.sdcms.dto.UserDto;
import lk.sdcms.exception.ValidationException;
import lk.sdcms.model.User;
import lk.sdcms.service.AuthenticationService;
import lk.sdcms.util.HttpResponses;
import lk.sdcms.util.JsonMapper;

import java.io.IOException;

/**
 * Establishes an authenticated session.
 *
 * <p>Thin by design: read the request, call one service method, write the
 * response. All credential logic lives in AuthenticationService, so it can be
 * unit tested without a servlet container.
 */
@WebServlet(name = "LoginServlet", urlPatterns = "/api/auth/login")
public class LoginServlet extends HttpServlet {

    /** Session attribute key, referenced by AuthFilter and every other servlet. */
    public static final String SESSION_USER = "authenticatedUser";

    private AuthenticationService authService;

    @Override
    public void init() {
        this.authService = new AuthenticationService(new UserDao());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        try {
            LoginRequest credentials = JsonMapper.fromJson(
                    new String(request.getInputStream().readAllBytes()),
                    LoginRequest.class);

            if (credentials == null) {
                HttpResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Request body is missing or malformed", request.getRequestURI());
                return;
            }

            User user = authService.authenticate(
                    credentials.getUsername(), credentials.getPassword());

            // Invalidate any prior session before establishing the new one.
            // Reusing an existing session id across a privilege change is the
            // session fixation vulnerability.
            HttpSession existing = request.getSession(false);
            if (existing != null) {
                existing.invalidate();
            }

            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_USER, user);
            session.setMaxInactiveInterval(30 * 60);

            HttpResponses.writeJson(response, HttpServletResponse.SC_OK,
                    UserDto.from(user));

        } catch (ValidationException e) {
            // 401 for any credential failure. The message is already uniform,
            // so this does not reveal whether the username exists.
            HttpResponses.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    e.getMessage(), request.getRequestURI());

        } catch (Exception e) {
            HttpResponses.writeError(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Login failed unexpectedly", request.getRequestURI());
        }
    }
}
