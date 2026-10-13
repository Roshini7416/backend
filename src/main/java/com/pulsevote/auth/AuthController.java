package com.pulsevote.auth;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

// /api/auth/register, /login, /logout, /me
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(AuthService authService, @Value("${app.cookie.secure}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody AuthRequests.Register body) {
        var result = authService.register(body);
        return withSessionCookie(HttpStatus.CREATED, result);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequests.Login body, HttpServletRequest request) {
        var result = authService.login(body, request.getRemoteAddr());
        return withSessionCookie(HttpStatus.OK, result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        authService.logout(AuthInterceptor.readCookie(request)); // deletes the session row on the server
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(Map.of("message", "Logged out."));
    }

    // Used by React on page load/refresh to find out whether the cookie still belongs to a valid session.
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        AuthUser user = AuthInterceptor.requireUser(request);
        return ResponseEntity.ok(Map.of("user", user));
    }

    private ResponseEntity<Map<String, Object>> withSessionCookie(HttpStatus status, AuthService.LoginResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(result.token(), Duration.ofDays(authService.getSessionDays())).toString())
                .body(Map.of("user", result.user()));
    }

    // HttpOnly: JavaScript cannot read it (protects against token theft via XSS).
    // SameSite=Lax: the browser does not send it on cross-site POST requests (CSRF defence).
    // Secure: only sent over HTTPS (set COOKIE_SECURE=true in production).
    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthInterceptor.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
