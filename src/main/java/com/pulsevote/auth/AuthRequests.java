package com.pulsevote.auth;

// JSON bodies sent by React to the auth endpoints.
public class AuthRequests {

    public record Register(String fullName, String email, String password, String confirmPassword) {
    }

    public record Login(String email, String password) {
    }

    private AuthRequests() {
    }
}
