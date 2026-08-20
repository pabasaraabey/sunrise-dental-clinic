package lk.sdcms.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import lk.sdcms.dto.UserDto;
import lk.sdcms.model.User;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;

/**
 * Reports who is currently logged in.
 *
 * <p>The client calls this on page load to decide which menu options to render.
 * That rendering is cosmetic only — authorisation is enforced by AuthFilter on
 * every request regardless of what the client chose to display.
 */
@WebServlet(name = "SessionServlet", urlPatterns = "/api/auth/session")
public class SessionServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);
        User user = (session == null)
                ? null
                : (User) session.getAttribute(LoginServlet.SESSION_USER);

        if (user == null) {
            HttpResponses.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Not signed in", request.getRequestURI());
            return;
        }
        HttpResponses.writeJson(response, HttpServletResponse.SC_OK, UserDto.from(user));
    }
}
