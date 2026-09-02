package com.prenotazioni.setting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Value("${prenotazioni.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${prenotazioni.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String allowedMethods;

    @Value("${prenotazioni.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${prenotazioni.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${prenotazioni.cors.max-age:3600}")
    private long maxAge;

    SecurityConfig(JwtAuthFilter jwtAuthFilter, ApiAuthenticationEntryPoint apiAuthenticationEntryPoint, ApiAccessDeniedHandler apiAccessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.apiAuthenticationEntryPoint = apiAuthenticationEntryPoint;
        this.apiAccessDeniedHandler = apiAccessDeniedHandler;
    }

    private static List<String> splitConfigList(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // NOTA: la console H2 non e' mai stata attivata in questo progetto
    // (manca 'spring.h2.console.enabled=true', e il DB usato e' sempre Postgres).
    // Se in futuro serve per debug locale, va aggiunta una security matcher chain
    // dedicata SOLO per '/h2-console/**' con frameOptions rilassato, invece di
    // disabilitare la protezione clickjacking per l'intera applicazione.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Per le preflight requests
                        .requestMatchers(
                                "/api/auth/login",
                                "/auth/login",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(splitConfigList(allowedOrigins));
        configuration.setAllowedMethods(splitConfigList(allowedMethods));

        if ("*".equals(allowedHeaders.trim())) {
            configuration.addAllowedHeader("*");
        } else {
            configuration.setAllowedHeaders(splitConfigList(allowedHeaders));
        }

        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
