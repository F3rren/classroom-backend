package com.classroom.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * The session cookies, and the one place that knows their names.
 *
 * A browser client cannot hold the tokens safely: anything JavaScript can read, an injected
 * script can read too, and the refresh token is the worse of the two to lose because it
 * outlives the access token by weeks. Carrying them in HttpOnly cookies instead puts them out
 * of reach of the page entirely.
 *
 * The tokens keep travelling in the response body as well, so a non-browser client - a mobile
 * app, a script, the tests - can go on using the Authorization header exactly as before.
 * Nothing that works today stops working; the cookies are an addition, not a replacement.
 *
 * Both halves of the exchange live here on purpose: JwtAuthFilter reads the access cookie and
 * AuthController writes both, and the two would drift apart the first time a name changed.
 */
public final class SessionCookies {

    /** Carries the access token, read by JwtAuthFilter on every protected request. */
    public static final String ACCESS_TOKEN = "access_token";

    /** Carries the refresh token, read by POST /auth/refresh and POST /auth/logout. */
    public static final String REFRESH_TOKEN = "refresh_token";

    private SessionCookies() {
    }

    /**
     * Reads a cookie value, or null when the request carries no such cookie.
     *
     * @param request the incoming request
     * @param name    the cookie name, one of the constants above
     * @return the value, or null when absent or empty
     */
    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }

    /**
     * Builds a session cookie.
     *
     * SameSite=Lax rather than Strict: Strict would withhold the cookie on a plain link into
     * the application from anywhere else, and the user would land on a login page despite
     * having a live session. Lax still keeps it off cross-site POSTs, which is what matters
     * here given CSRF is disabled.
     *
     * @param name   the cookie name
     * @param value  the token
     * @param maxAge how long the browser should keep it
     * @param secure true to mark it Secure - it must be false over plain HTTP, or the browser
     *               discards the cookie and the session silently never starts
     * @return the cookie, ready for the Set-Cookie header
     */
    public static ResponseCookie build(String name, String value, Duration maxAge, boolean secure) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    /**
     * Builds the cookie that deletes an existing one: same name and path, no value, expiring
     * at once. A browser only replaces a cookie when path and name match the original.
     *
     * @param name   the cookie to delete
     * @param secure the same flag used when it was issued
     * @return the cookie that clears it
     */
    public static ResponseCookie expire(String name, boolean secure) {
        return build(name, "", Duration.ZERO, secure);
    }

    /** The header name to add each cookie under. */
    public static String header() {
        return HttpHeaders.SET_COOKIE;
    }
}
