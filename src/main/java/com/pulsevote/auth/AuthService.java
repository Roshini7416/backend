package com.pulsevote.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.pulsevote.exception.ApiException;

// Registration, login, logout and session lookup.
@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final String BAD_LOGIN = "Invalid email or password.";

    private final UserRepository users;
    private final LoginRateLimiter rateLimiter;
    private final int sessionDays;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    // Compared against when the email does not exist, so "unknown email" and "wrong password"
    // take about the same time and cannot be told apart.
    private final String dummyHash = encoder.encode("not-a-real-password-1");

    public AuthService(UserRepository users, LoginRateLimiter rateLimiter,
                       @Value("${app.session.days}") int sessionDays) {
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.sessionDays = sessionDays;
    }

    public int getSessionDays() {
        return sessionDays;
    }

    /** Result of register/login: the user and the raw session token to put in the cookie. */
    public record LoginResult(AuthUser user, String token) {
    }

    public LoginResult register(AuthRequests.Register req) {
        if (req == null) throw bad("Request body is missing.");

        String name = cleanName(req.fullName());
        if (name.length() < 2 || name.length() > 100) {
            throw bad("Full name must be between 2 and 100 characters.");
        }
        String email = cleanEmail(req.email());
        validatePassword(req.password());
        if (!req.password().equals(req.confirmPassword())) {
            throw bad("Passwords do not match.");
        }

        long id;
        try {
            id = users.insertUser(name, email, encoder.encode(req.password()));
        } catch (DuplicateKeyException e) { // the UNIQUE key also protects against two simultaneous sign-ups
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists. Please log in.");
        }
        return new LoginResult(new AuthUser(id, name, email), startSession(id));
    }

    public LoginResult login(AuthRequests.Login req, String clientAddress) {
        if (req == null || req.email() == null || req.password() == null
                || req.email().isBlank() || req.password().isEmpty()) {
            throw bad("Please enter your email and password.");
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String limitKey = clientAddress + "|" + email;
        if (!rateLimiter.tryAcquire(limitKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please wait 15 minutes and try again.");
        }

        var row = users.findByEmail(email);
        boolean ok = encoder.matches(req.password(), row.map(UserRepository.UserRow::passwordHash).orElse(dummyHash))
                && row.isPresent();
        if (!ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN);
        }

        rateLimiter.reset(limitKey);
        users.deleteExpiredSessions(); // housekeeping
        UserRepository.UserRow u = row.get();
        return new LoginResult(new AuthUser(u.id(), u.fullName(), u.email()), startSession(u.id()));
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) users.deleteSession(hash(token));
    }

    /** Looks up the user for a cookie token. Throws 401 (with a clear message) if missing or expired. */
    public AuthUser userForToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please log in to continue.");
        }
        String h = hash(token);
        var session = users.findSession(h)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please log in to continue."));
        if (session.expired()) {
            users.deleteSession(h);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your session has expired. Please log in again.");
        }
        return session.user();
    }

    // ---------- helpers ----------

    // 256-bit random token. Only its SHA-256 hash is stored, so a database leak does not leak live sessions.
    private String startSession(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        users.insertSession(userId, hash(token), sessionDays);
        return token;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String cleanName(String raw) {
        if (raw == null) throw bad("Full name is required.");
        // remove control characters, collapse repeated spaces
        return raw.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    private String cleanEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) throw bad("Email address is required.");
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) throw bad("Please enter a valid email address.");
        return email;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) throw bad("Password must be at least 8 characters.");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) { // BCrypt only uses the first 72 bytes
            throw bad("Password is too long (maximum 72 bytes).");
        }
        if (!password.matches(".*\\p{L}.*") || !password.matches(".*\\d.*")) {
            throw bad("Password must contain at least one letter and one number.");
        }
    }

    private ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
