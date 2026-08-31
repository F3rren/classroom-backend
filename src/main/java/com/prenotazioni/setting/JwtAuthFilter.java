package com.prenotazioni.setting;

import com.prenotazioni.security.AppPrincipal;
import com.prenotazioni.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    
    private final JwtService jwtService;

    JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        String origin = request.getHeader("Origin");
        
        logger.info("JWT Filter - Method: {}, Path: {}, Origin: {}", method, path, origin);
        
        // Salta il filtro per le rotte pubbliche (con o senza /api prefix)
        if (path.startsWith("/api/auth/login") ||
            path.startsWith("/auth/login") ||
            path.startsWith("/h2-console")) {
            logger.info("JWT Filter - Rotta pubblica, skip validazione");
            filterChain.doFilter(request, response);
            return;
        }
        
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if (token != null && jwtService.validateToken(token)) {
            String email = jwtService.getEmailFromToken(token);
            Long id = jwtService.getUserIdFromToken(token);
            String ruolo = jwtService.getRuoloFromToken(token);

            AppPrincipal principal = new AppPrincipal(id, email, ruolo);
            List<GrantedAuthority> authorities = ruolo != null
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + ruolo.toUpperCase(Locale.ROOT)))
                    : List.of();

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
