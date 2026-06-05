package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * LF-201: CORS must be registered in the Spring Security filter chain so that
 * preflight OPTIONS requests are allowed BEFORE security checks run.
 * A @CrossOrigin annotation alone is useless if the OPTIONS preflight is rejected
 * by security before reaching the controller.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Wire the externalized CorsConfigurationSource so Spring Security
            // processes CORS before any auth checks — this is the key fix for LF-201
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf().disable()
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
