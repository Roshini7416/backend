package com.pulsevote.auth;

// The logged-in user as sent to React. Never contains the password or its hash.
public record AuthUser(long id, String fullName, String email) {
}
