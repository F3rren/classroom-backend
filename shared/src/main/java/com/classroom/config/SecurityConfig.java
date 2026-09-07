package com.classroom.config;

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

    /**
     * The paths reachable without a token, comma separated.
     *
     * This is the only part of this configuration that differs from service to service, and
     * that is why it is a property rather than a list written in the code: the security
     * chain, the CORS setup and the two error handlers are identical everywhere, and
     * duplicating them per service would mean they drift apart sooner or later.
     *
     * The default covers only the OpenAPI documentation: a service that declares nothing
     * stays fully protected, which is the right default to get wrong.
     */
    @Value("${classroom.security.public-paths:/v3/api-docs,/v3/api-docs/**,/swagger-ui/**,/swagger-ui.html}")
    private String publicPaths;

    @Value("${classroom.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${classroom.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String allowedMethods;

    @Value("${classroom.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${classroom.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${classroom.cors.max-age:3600}")
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

    // NOTE: the H2 console has never been enabled in this project
    // ('spring.h2.console.enabled=true' is absent, and the database in use is always
    // Postgres). If it is ever wanted for local debugging, add a security matcher chain
    // dedicated ONLY to '/h2-console/**' with frameOptions relaxed, rather than switching
    // clickjacking protection off for the whole application.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // for the preflight requests
                        .requestMatchers(splitConfigList(publicPaths).toArray(new String[0]))
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
