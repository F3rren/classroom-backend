package com.classroom.config;

import com.classroom.model.Role;
import com.classroom.security.AppPrincipal;
import com.classroom.security.JwtVerifier;
import com.classroom.security.SessionCookies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    
    private final JwtVerifier jwtVerifier;

    JwtAuthFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();

        // DEBUG and not INFO: this filter is crossed by EVERY request (static assets
        // included), so at INFO it produced two lines per request, burying the useful events.
        logger.debug("JWT Filter - {} {}", method, path);

        // Skip the filter on the public routes (with or without the /api prefix)
        if (path.startsWith("/api/auth/login") ||
            path.startsWith("/auth/login") ||
            path.startsWith("/h2-console")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = readAccessToken(request);
        if (token != null && jwtVerifier.validateToken(token)) {
            String email = jwtVerifier.getEmailFromToken(token);
            Long id = jwtVerifier.getUserIdFromToken(token);
            String role = jwtVerifier.getRoleFromToken(token);
            String name = jwtVerifier.getNameFromToken(token);
            String username = jwtVerifier.getUsernameFromToken(token);

            AppPrincipal principal = new AppPrincipal(id, email, username, name, role);

            // The "ROLE_" prefix hasRole(...) expects comes from Role.toAuthority(), so it
            // is no longer rebuilt by hand here. An unrecognised role does not fail the
            // request: it simply leaves it with no authority, exactly as a missing claim
            // would, and the security policy is what denies access.
            List<GrantedAuthority> authorities;
            try {
                Role typedRole = Role.from(role);
                authorities = typedRole != null
                        ? List.of(new SimpleGrantedAuthority(typedRole.toAuthority()))
                        : List.of();
            } catch (IllegalArgumentException e) {
                logger.warn("JWT filter - unrecognised role in the token, no authority granted");
                authorities = List.of();
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            logger.debug("JWT filter - authenticated userId={} role={}", id, role);
        } else if (token != null) {
            // A token that is present but invalid (expired, wrongly signed, tampered with):
            // until now this passed in silence and the request arrived unauthenticated
            // without leaving a trace.
            // It is a security signal, so WARN.
            logger.warn("JWT filter - invalid or expired token on {} {}", method, path);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Finds the access token, from the Authorization header or from the session cookie.
     *
     * The header comes first so nothing that works today changes behaviour: a client sending
     * both keeps being authenticated by the header exactly as before. The cookie is the
     * fallback, and it is what lets a browser hold the token out of JavaScript's reach - see
     * SessionCookies.
     *
     * @param request the incoming request
     * @return the token, or null when the request carries neither
     */
    private String readAccessToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String fromHeader = authHeader.substring(7);
            if (!fromHeader.isBlank()) {
                return fromHeader;
            }
        }

        return SessionCookies.read(request, SessionCookies.ACCESS_TOKEN);
    }
}
