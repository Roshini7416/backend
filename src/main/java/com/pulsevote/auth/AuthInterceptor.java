package com.pulsevote.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.pulsevote.exception.ApiException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Runs before every /api request:
//  1. For POST requests, a browser Origin header must be one of our allowed frontends (CSRF defence,
//     in addition to the SameSite cookie and the JSON-only request bodies).
//  2. If the request carries a valid session cookie, the user is attached to the request.
//     Controllers call requireUser(...) for actions that need login, or currentUser(...) when login is optional.
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String COOKIE_NAME = "pv_session";
    private static final String USER_ATTR = "pulsevote.user";
    private static final String ERROR_ATTR = "pulsevote.authError";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final AuthService authService;
    private final List<String> allowedOrigins;

    public AuthInterceptor(AuthService authService, @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.authService = authService;
        this.allowedOrigins = Arrays.asList(allowedOrigins);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!SAFE_METHODS.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin != null && !allowedOrigins.contains(origin)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Request blocked: untrusted origin.");
            }
        }

        String token = readCookie(request);
        if (token != null) {
            try {
                request.setAttribute(USER_ATTR, authService.userForToken(token));
            } catch (ApiException e) {
                request.setAttribute(ERROR_ATTR, e.getMessage());
            }
        }
        return true;
    }

    public static String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    /** The logged-in user, or null if nobody is logged in. */
    public static AuthUser currentUser(HttpServletRequest request) {
        return (AuthUser) request.getAttribute(USER_ATTR);
    }

    /** The logged-in user, or a 401 error ("log in" / "session expired"). */
    public static AuthUser requireUser(HttpServletRequest request) {
        AuthUser user = currentUser(request);
        if (user == null) {
            Object message = request.getAttribute(ERROR_ATTR);
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    message == null ? "Please log in to continue." : message.toString());
        }
        return user;
    }
}
