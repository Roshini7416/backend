package com.pulsevote.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.pulsevote.auth.AuthInterceptor;

// CORS: the browser blocks React (port 5173) from calling Spring Boot (port 8080)
// unless the backend says "this origin is allowed". This class says it.
// allowCredentials(true) lets the browser send the login cookie; because of that the allowed
// origins must be listed exactly (a "*" wildcard is not allowed together with cookies).
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final AuthInterceptor authInterceptor;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins,
                      AuthInterceptor authInterceptor) {
        this.allowedOrigins = allowedOrigins;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type")
                .allowCredentials(true);
    }

    // Runs before every /api controller: checks the Origin of POST requests and loads the logged-in user.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}
