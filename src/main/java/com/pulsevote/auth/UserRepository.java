package com.pulsevote.auth;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// SQL for users and login sessions (parameterized queries only).
@Repository
public class UserRepository {

    // Internal row including the hash. Never serialized to JSON.
    public record UserRow(long id, String fullName, String email, String passwordHash) {
    }

    // A session found by its token hash. expired is computed by MySQL (UTC clock).
    public record SessionRow(long sessionId, AuthUser user, boolean expired) {
    }

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.query("SELECT id, full_name, email, password_hash FROM users WHERE email = ?",
                (rs, i) -> new UserRow(rs.getLong("id"), rs.getString("full_name"),
                        rs.getString("email"), rs.getString("password_hash")), email)
                .stream().findFirst();
    }

    // The UNIQUE key on users.email throws DuplicateKeyException if the email is already registered.
    public long insertUser(String fullName, String email, String passwordHash) {
        jdbc.update("INSERT INTO users (full_name, email, password_hash) VALUES (?, ?, ?)",
                fullName, email, passwordHash);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return id == null ? -1 : id;
    }

    public void insertSession(long userId, String tokenHash, int days) {
        jdbc.update("INSERT INTO user_sessions (user_id, token_hash, expires_at) "
                + "VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? DAY))", userId, tokenHash, days);
    }

    public Optional<SessionRow> findSession(String tokenHash) {
        return jdbc.query("SELECT s.id AS session_id, s.expires_at <= UTC_TIMESTAMP() AS expired, "
                + "u.id, u.full_name, u.email FROM user_sessions s JOIN users u ON u.id = s.user_id "
                + "WHERE s.token_hash = ?",
                (rs, i) -> new SessionRow(rs.getLong("session_id"),
                        new AuthUser(rs.getLong("id"), rs.getString("full_name"), rs.getString("email")),
                        rs.getBoolean("expired")), tokenHash)
                .stream().findFirst();
    }

    public void deleteSession(String tokenHash) {
        jdbc.update("DELETE FROM user_sessions WHERE token_hash = ?", tokenHash);
    }

    public void deleteExpiredSessions() {
        jdbc.update("DELETE FROM user_sessions WHERE expires_at <= UTC_TIMESTAMP()");
    }
}
