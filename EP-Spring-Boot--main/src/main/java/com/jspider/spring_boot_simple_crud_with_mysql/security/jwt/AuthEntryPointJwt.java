package com.jspider.spring_boot_simple_crud_with_mysql.security.jwt;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security {@link AuthenticationEntryPoint} implementation that emits a
 * structured HTTP 401 (Unauthorized) JSON response when an unauthenticated
 * request reaches a protected endpoint.
 *
 * <p>Spring Security's {@code ExceptionTranslationFilter} delegates to this
 * entry point's {@link #commence(HttpServletRequest, HttpServletResponse,
 * AuthenticationException)} method whenever an anonymous (unauthenticated)
 * request attempts to access a path that requires authentication. The
 * default Spring Boot behavior would return an HTML error page; this entry
 * point overrides that behavior to return a predictable JSON document so that
 * REST API clients can parse the failure programmatically.</p>
 *
 * <p>The response body is a JSON object containing exactly four keys:</p>
 * <ul>
 *   <li>{@code status}  &mdash; the HTTP status code (always 401)</li>
 *   <li>{@code error}   &mdash; the short identifier "Unauthorized"</li>
 *   <li>{@code message} &mdash; the {@link AuthenticationException#getMessage()}
 *       value (a generic, client-safe description such as
 *       "Full authentication is required to access this resource")</li>
 *   <li>{@code path}    &mdash; the application-relative servlet path
 *       (e.g. {@code /product/findAllProduct})</li>
 * </ul>
 *
 * <p>This component is annotated with {@link Component} so that Spring's
 * component scan registers it automatically. {@code SecurityConfig} injects
 * it via {@code @Autowired} field injection and wires it into the security
 * filter chain via
 * {@code http.exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))}.
 * </p>
 *
 * <p><b>Security guarantees</b>: this implementation deliberately avoids
 * leaking sensitive information in the response body. Specifically, it does
 * NOT include the exception class name, stack trace, root-cause details, the
 * value of the {@code Authorization} header, or any user-supplied request
 * payload. Detailed failure reasons are written only to the server-side log
 * at {@code ERROR} level via SLF4J.</p>
 *
 * <p><b>Scope</b>: this class handles the 401 (unauthenticated) case ONLY.
 * Forbidden access (HTTP 403, when an authenticated user lacks the required
 * role) is dispatched by Spring Security's {@code AccessDeniedHandler} and is
 * translated to a JSON response by {@code GlobalExceptionHandler}.</p>
 *
 * @see AuthenticationEntryPoint
 * @see org.springframework.security.web.access.ExceptionTranslationFilter
 */
@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    /**
     * Class-level SLF4J logger used to record unauthorized access attempts at
     * {@code ERROR} level. The logger is {@code private static final}
     * following the canonical SLF4J idiom.
     */
    private static final Logger log = LoggerFactory.getLogger(AuthEntryPointJwt.class);

    /**
     * Builds and writes a structured HTTP 401 JSON response in reply to an
     * unauthenticated request that targets a protected endpoint.
     *
     * <p>The method performs the following steps in order:</p>
     * <ol>
     *   <li>Logs the failure at {@code ERROR} level with only the exception
     *       message (no stack trace) so that operators can detect
     *       misconfigured clients without polluting the server log.</li>
     *   <li>Sets the response {@code Content-Type} header to
     *       {@code application/json} via the
     *       {@link MediaType#APPLICATION_JSON_VALUE} constant.</li>
     *   <li>Sets the HTTP status to 401 via the
     *       {@link HttpServletResponse#SC_UNAUTHORIZED} constant.</li>
     *   <li>Constructs a four-key {@link Map} containing {@code status},
     *       {@code error}, {@code message}, and {@code path}. The
     *       {@code path} value is taken from
     *       {@link HttpServletRequest#getServletPath()} (which strips the
     *       servlet context path and returns the application-relative URI).</li>
     *   <li>Serializes the map to JSON using a freshly-instantiated Jackson
     *       {@link ObjectMapper} and writes it directly to the response
     *       output stream.</li>
     * </ol>
     *
     * <p>The {@link IOException} and {@link ServletException} declared in the
     * throws clause are part of the {@link AuthenticationEntryPoint} interface
     * contract and are propagated unchanged so that Spring Security's filter
     * chain may handle them.</p>
     *
     * @param request       the inbound HTTP request that failed authentication
     * @param response      the HTTP response to which the 401 JSON document is
     *                      written
     * @param authException the {@link AuthenticationException} produced by
     *                      Spring Security; its
     *                      {@link AuthenticationException#getMessage() message}
     *                      is logged server-side and echoed in the response
     *                      {@code message} field. Cause and class details are
     *                      intentionally NOT exposed.
     * @throws IOException      if the response output stream cannot be written
     * @throws ServletException if the servlet container reports a generic
     *                          servlet-layer failure
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        // Server-side log only — no client leakage of stack trace or root cause.
        log.error("Unauthorized error: {}", authException.getMessage());

        // Force a JSON content type so REST clients can parse the body programmatically.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // HTTP 401 — the standard "missing or invalid credentials" status code.
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Build the response body with exactly four keys (status, error, message, path).
        // No timestamp/requestId/traceId/correlationId — kept minimal per AAP §0.5.1.4.
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", authException.getMessage());
        // getServletPath() strips the context path so REST clients see the
        // application-relative URI (e.g. /product/findAllProduct).
        body.put("path", request.getServletPath());

        // A fresh ObjectMapper is acceptable here because the entry point fires
        // only on rare, anomalous unauthenticated-request events.
        new ObjectMapper().writeValue(response.getOutputStream(), body);
    }
}
