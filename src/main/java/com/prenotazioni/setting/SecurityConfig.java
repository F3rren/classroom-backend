package com.prenotazioni.setting;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register", "/h2-console/**").permitAll()
                .requestMatchers("/api/me").authenticated()
                .requestMatchers("/api/admin/**").authenticated() // Tutti gli endpoint admin richiedono autenticazione
                .requestMatchers("/api/rooms/**").authenticated() // Endpoint aule accessibile a tutti gli utenti autenticati
                .requestMatchers("/api/prenotazioni/**").authenticated() // Endpoint prenotazioni accessibile a tutti gli utenti autenticati
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers.frameOptions().disable()); // Per H2 console
        return http.build();
    }
}
