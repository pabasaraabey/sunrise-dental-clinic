package lk.sdcms.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import lk.sdcms.model.Role;
import lk.sdcms.model.User;
import lk.sdcms.servlet.LoginServlet;
import lk.sdcms.util.HttpResponses;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Enforces authentication and role-based authorisation on every API call.
 *
 * <p>This is the security boundary. The browser client hides menu options the
 * current role may not use, but that is presentation only — anyone can open
 * developer tools, or call the endpoints directly with Postman, and a hidden
 * menu item is no obstacle at all. Authorisation has to be decided here, on the
 * server, where the caller cannot reach it.
 *
 * <p>A Filter is used rather than a check inside each servlet because a check
 * repeated in a dozen places is a check that will eventually be forgotten in
 * one of them. Intercepting centrally means a new endpoint is protected by
 * default rather than by remembering.
 *
 * <p>Only staff account management is restricted to the administrator. The
 * receptionist carries every operational duty, including treatment prices —
 * a deliberate departure from separation of duties, justified by the clinic
 * having too few staff to divide the work, with audit_log as the compensating
 * control.
 */
@WebFilter(filterName = "AuthFilter", urlPatterns = "/api/*")
public class AuthFilter implements Filter {

    /** Endpoints reachable without a session, for obvious reasons. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/session"
    );

    /** Path prefix to the roles permitted on it. Longest match wins. */
    private static final Map<String, Set<Role>> ROLE_RULES = Map.of(
            "/api/users", Set.of(Role.ADMINISTRATOR)
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI()
                .substring(request.getContextPath().length());

        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(req, res);
            return;
        }

        HttpSession session = request.getSession(false);
        User user = (session == null)
                ? null
                : (User) session.getAttribute(LoginServlet.SESSION_USER);

        if (user == null) {
            // 401, not 403: the caller has not identified themselves at all.
            HttpResponses.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Please sign in to continue", path);
            return;
        }

        Set<Role> permitted = resolveRule(path);

        if (permitted != null && !permitted.contains(user.getRole())) {
            // 403, not 401: identity is established, but this role may not do
            // this. Conflating the two leaves the client unable to tell
            // "sign in again" from "you cannot do this".
            HttpResponses.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Only an administrator can manage staff accounts", path);
            return;
        }

        chain.doFilter(req, res);
    }

    /** Longest matching prefix, so specific rules override general ones. */
    private Set<Role> resolveRule(String path) {
        return ROLE_RULES.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .sorted((a, b) -> Integer.compare(
                        b.getKey().length(), a.getKey().length()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /** Convenience for servlets that need the caller's identity. */
    public static User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return (session == null)
                ? null
                : (User) session.getAttribute(LoginServlet.SESSION_USER);
    }
}
