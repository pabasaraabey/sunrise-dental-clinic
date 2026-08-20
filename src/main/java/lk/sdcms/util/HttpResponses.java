package lk.sdcms.util;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes JSON responses in a consistent shape.
 *
 * <p>Without a framework there is no exception handler wiring error responses
 * automatically, so every servlet would otherwise invent its own error format.
 * Centralising it here means the browser client can rely on one structure.
 */
public final class HttpResponses {

    private HttpResponses() {
    }

    public static void writeJson(HttpServletResponse response, int status, Object body)
            throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JsonMapper.toJson(body));
    }

    public static void writeError(HttpServletResponse response, int status,
                                  String message, String path) throws IOException {

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", status);
        error.put("message", message);
        error.put("path", path);

        writeJson(response, status, error);
    }

    public static void writeMessage(HttpServletResponse response, int status,
                                    String message) throws IOException {
        writeJson(response, status, Map.of("message", message));
    }
}
