package com.classroom.exception;

/**
 * The credentials offered are not the right ones. Becomes a 401.
 *
 * It is NOT the same thing as the 401 ApiAuthenticationEntryPoint answers with. That one is
 * a protected resource refusing a request that carries no valid token, and it happens in the
 * security filter chain, before any controller. This one is a login refusing the password,
 * inside the controller, on an endpoint that is deliberately public.
 *
 * The distinction decides one concrete thing: this response carries NO WWW-Authenticate
 * header, although RFC 9110 asks a 401 to. There is no challenge to send that would help -
 * the endpoint does not use HTTP authentication, it reads a JSON body - and answering
 * "Bearer" would tell the caller to do the one thing that cannot possibly work. A challenge
 * that misleads is worse than an absent one.
 *
 * It exists so that the status stops being a controller's private business: AuthController
 * used to build this response by hand, so a second endpoint needing it would have had to
 * build it again.
 */
public class AuthenticationFailedException extends ApplicationException {

    public AuthenticationFailedException(String errorCode, String message, String userMessage) {
        super(errorCode, message, userMessage);
    }
}
