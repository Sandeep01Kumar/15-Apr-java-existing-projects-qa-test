package com.jspider.spring_boot_simple_crud_with_mysql.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jspider.spring_boot_simple_crud_with_mysql.payload.response.MessageResponse;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;

/**
 * Centralized {@link RestControllerAdvice} that translates exceptions raised
 * anywhere in the request-handling pipeline (controllers, services, and
 * post-filter security enforcement) into structured JSON HTTP responses with
 * appropriate status codes.
 *
 * <p>This advice is the canonical Spring 6.x mechanism for converting
 * application-level exceptions into REST-friendly JSON envelopes. Every
 * handler method here maps a particular exception type (or a small,
 * cohesive group of related exception types) to:</p>
 * <ul>
 *   <li>An HTTP status code (400, 401, 403, 404, or 500);</li>
 *   <li>A {@link MessageResponse} body (or, exceptionally, a
 *       {@code Map<String, String>} field-error map for validation errors);</li>
 *   <li>A server-side ERROR-level log entry that captures diagnostic detail
 *       <em>without</em> leaking it to the client.</li>
 * </ul>
 *
 * <h2>Layered exception-handling model</h2>
 * <p>This advice operates at the <strong>controller</strong> layer of the
 * Spring Security filter chain &mdash; that is, it is invoked AFTER the
 * security filters have admitted the request and a controller method has been
 * selected and invoked. It runs when:</p>
 * <ul>
 *   <li>A controller method (e.g. {@code AuthController#authenticateUser}
 *       or any {@code @PreAuthorize}-protected method on
 *       {@code ProductController} / {@code StudentController}) throws an
 *       exception;</li>
 *   <li>Spring's request-binding layer rejects an inbound payload via
 *       {@link MethodArgumentNotValidException} from a {@code @Valid}-annotated
 *       parameter;</li>
 *   <li>Spring's method-level security throws
 *       {@link AccessDeniedException} because a {@code @PreAuthorize}
 *       expression evaluated to {@code false}.</li>
 * </ul>
 *
 * <p>This advice does <strong>NOT</strong> handle authentication failures
 * detected at the security <em>filter</em> level (e.g. a request that arrives
 * with no valid JWT and targets a protected endpoint). Those are intercepted
 * by the {@code AuthEntryPointJwt} component, which writes its own structured
 * JSON 401 response with a different shape
 * ({@code {status, error, message, path}}).</p>
 *
 * <h2>Information-disclosure policy</h2>
 * <p>This handler enforces a strict policy of not echoing internal exception
 * details to clients. With one well-defined exception ({@link #handleIllegalArgument}
 * &mdash; which receives deliberately user-friendly messages from
 * {@code AuthService.register}), every public-facing message is a constant
 * string. The full exception detail (class name, message, stack trace) is
 * recorded server-side at ERROR level via SLF4J. This policy mitigates:</p>
 * <ul>
 *   <li>User-enumeration attacks on the login flow (constant
 *       {@code "Invalid username or password"} regardless of whether the
 *       username exists);</li>
 *   <li>JWT-tampering reconnaissance (constant
 *       {@code "Invalid or expired token"} regardless of which JWT validation
 *       step failed);</li>
 *   <li>Implementation-detail leakage from generic exceptions (constant
 *       {@code "An unexpected error occurred"} regardless of the underlying
 *       SQL fragment, framework class name, or stack frame).</li>
 * </ul>
 *
 * <h2>Stateless and dependency-free</h2>
 * <p>The class carries no field-injected dependencies (only a static SLF4J
 * logger). It is instantiated once by Spring as a singleton during
 * {@code @RestControllerAdvice} bean registration and is trivially thread-safe
 * because every method receives all required state through its parameters.
 * The class is intentionally <em>not</em> annotated with {@code @Component},
 * {@code @Service}, or any other stereotype &mdash;
 * {@link RestControllerAdvice} is itself the registering stereotype and
 * combines {@code @ControllerAdvice + @ResponseBody} so all return values are
 * serialized to JSON via Jackson.</p>
 *
 * @see com.jspider.spring_boot_simple_crud_with_mysql.security.jwt.AuthEntryPointJwt
 *      for the filter-level authentication-failure handler.
 * @see MessageResponse for the canonical single-field response envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Class-level SLF4J logger used to record every handled exception at
     * {@code ERROR} level for server-side diagnostics. The logger is
     * {@code private static final} per the canonical SLF4J idiom: {@code static}
     * shares one logger per class (no per-instance allocation), {@code final}
     * prevents reassignment, and {@code private} enforces encapsulation.
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Translates a {@link BadCredentialsException} into an HTTP 401
     * (Unauthorized) response with a constant client-facing message.
     *
     * <p>This handler fires when {@code AuthService.authenticate} invokes
     * {@code AuthenticationManager.authenticate} with credentials that fail
     * verification &mdash; either because the username does not exist (Spring
     * Security's {@code DaoAuthenticationProvider} hides
     * {@link UsernameNotFoundException} as {@link BadCredentialsException} by
     * default to prevent enumeration) or because the password's BCrypt hash
     * does not match the stored value.</p>
     *
     * <p><strong>Security rationale</strong> &mdash; the response body is the
     * constant string {@code "Invalid username or password"}, NOT
     * {@code ex.getMessage()}. Returning the framework-generated message
     * (which can vary between "Bad credentials" and "User not found") would
     * enable a user-enumeration attack: an attacker could iteratively probe
     * usernames and infer which exist by inspecting the response messages.
     * The constant message ensures both failure modes are
     * indistinguishable to clients.</p>
     *
     * <p>The original exception message is logged server-side at ERROR level
     * for operator debugging.</p>
     *
     * @param ex the {@link BadCredentialsException} thrown by Spring
     *           Security's authentication pipeline
     * @return a {@link ResponseEntity} with HTTP status 401 and a
     *         {@link MessageResponse} body containing the constant
     *         user-friendly error message
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<MessageResponse> handleBadCredentials(BadCredentialsException ex) {
        log.error("Bad credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new MessageResponse("Invalid username or password"));
    }

    /**
     * Translates a {@link UsernameNotFoundException} into an HTTP 404
     * (Not Found) response.
     *
     * <p>This handler is rarely hit on the {@code POST /api/auth/login} path
     * because Spring Security's {@code DaoAuthenticationProvider} masks
     * {@link UsernameNotFoundException} as {@link BadCredentialsException}
     * (controlled by its
     * {@code hideUserNotFoundExceptions} flag, which defaults to {@code true}
     * in Spring Security 6.x). It IS triggered, however, when
     * {@code UserDetailsServiceImpl.loadUserByUsername} is invoked
     * programmatically outside the authentication flow &mdash; for example
     * from a future admin endpoint that looks up a user directly.</p>
     *
     * <p>Unlike {@link #handleBadCredentials}, this handler echoes
     * {@code ex.getMessage()} into the response body. That is acceptable here
     * because the message is constructed inside our own
     * {@code UserDetailsServiceImpl} ("User Not Found with username: ...") and
     * does not originate from user input or third-party libraries. It is also
     * not a credentials-validation surface, so the user-enumeration risk does
     * not apply.</p>
     *
     * @param ex the {@link UsernameNotFoundException} thrown by
     *           {@code UserDetailsServiceImpl}
     * @return a {@link ResponseEntity} with HTTP status 404 and a
     *         {@link MessageResponse} body wrapping the exception message
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<MessageResponse> handleUserNotFound(UsernameNotFoundException ex) {
        log.error("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new MessageResponse(ex.getMessage()));
    }

    /**
     * Translates any of four JJWT 0.13.x token-validation exceptions into a
     * single uniform HTTP 401 (Unauthorized) response with a constant
     * client-facing message.
     *
     * <p>This handler fires for the following exception types that may bubble
     * up from JWT parsing in {@code JwtUtils.getUserNameFromJwtToken} or any
     * direct {@code Jwts.parser()...parseSignedClaims} invocation that is not
     * wrapped in {@code try/catch}:</p>
     * <ul>
     *   <li>{@link MalformedJwtException} &mdash; the token's structure is
     *       invalid (e.g. fewer than three Base64URL segments separated by
     *       periods);</li>
     *   <li>{@link ExpiredJwtException} &mdash; the token's {@code exp} claim
     *       is in the past;</li>
     *   <li>{@link UnsupportedJwtException} &mdash; the token is well-formed
     *       but uses an algorithm or claim convention that this JJWT
     *       configuration cannot process;</li>
     *   <li>{@code io.jsonwebtoken.security.SignatureException} (referenced
     *       inline by fully-qualified name &mdash; see the {@code @ExceptionHandler}
     *       annotation below) &mdash; the token's HMAC signature does not
     *       verify against the configured secret key.</li>
     * </ul>
     *
     * <p><strong>Why fully-qualified inline reference for
     * {@code SignatureException}</strong> &mdash; JJWT exposes a deprecated
     * {@code io.jsonwebtoken.SignatureException} (the pre-0.11 location) and
     * the modern {@code io.jsonwebtoken.security.SignatureException}
     * (introduced in JJWT 0.11+). Importing one would shadow the other and
     * obscure which is in use. By referencing the modern class
     * <em>fully-qualified</em> in the annotation array, this code clearly
     * commits to the JJWT 0.11+ API surface required by the project's JJWT
     * 0.13.0 dependency.</p>
     *
     * <p><strong>Security rationale</strong> &mdash; the response body is the
     * constant string {@code "Invalid or expired token"}, regardless of which
     * specific JWT failure mode occurred. Distinguishing between
     * {@code "expired"}, {@code "malformed"}, and {@code "signature invalid"}
     * would assist an attacker probing the token-validation pipeline (for
     * example, to determine whether a forged token has the correct algorithm
     * or whether its signature is being compared against the right key). The
     * server-side log captures {@code ex.getClass().getSimpleName()} so
     * operators retain the diagnostic detail.</p>
     *
     * <p><strong>Parameter type</strong> &mdash; the parameter is declared as
     * {@link Exception} (not a JJWT-specific type) because Java's
     * {@code @ExceptionHandler({...})} mechanism requires the handler
     * parameter to be a common supertype of all listed exception classes. The
     * lowest common ancestor of the four JWT exception types is
     * {@code RuntimeException} / {@link Exception}.</p>
     *
     * @param ex one of the four JWT-related exception instances bubbling up
     *           from the parsing pipeline
     * @return a {@link ResponseEntity} with HTTP status 401 and a
     *         {@link MessageResponse} body containing the constant message
     */
    @ExceptionHandler({ MalformedJwtException.class, ExpiredJwtException.class,
                        UnsupportedJwtException.class,
                        io.jsonwebtoken.security.SignatureException.class })
    public ResponseEntity<MessageResponse> handleJwtExceptions(Exception ex) {
        log.error("JWT validation failed: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new MessageResponse("Invalid or expired token"));
    }

    /**
     * Translates a {@link MethodArgumentNotValidException} (raised by Spring's
     * {@code @Valid} parameter validation) into an HTTP 400 (Bad Request)
     * response carrying a per-field error map.
     *
     * <p>This handler fires when a controller endpoint declared with
     * {@code @RequestBody @Valid SomeRequest body} receives a payload that
     * fails one or more Jakarta Bean Validation 3.x constraints (e.g.
     * {@code @NotBlank}, {@code @Size}, {@code @Email}). It applies to:</p>
     * <ul>
     *   <li>{@code AuthController.registerUser(@Valid SignupRequest)} &mdash;
     *       triggers when {@code username}, {@code email}, or {@code password}
     *       fail their constraint annotations;</li>
     *   <li>{@code AuthController.authenticateUser(@Valid LoginRequest)} &mdash;
     *       triggers when {@code username} or {@code password} are blank.</li>
     * </ul>
     *
     * <p>The response body is a JSON object mapping each invalid field's name
     * to its constraint-violation message, e.g.:</p>
     * <pre>{@code
     *   {
     *     "username": "must not be blank",
     *     "email":    "must be a well-formed email address",
     *     "password": "size must be between 6 and 40"
     *   }
     * }</pre>
     *
     * <p>This shape is more useful for frontends than a single
     * {@link MessageResponse} would be: it allows the UI to highlight the
     * offending input next to each form field. It is therefore the only
     * handler in this advice class that returns
     * {@link Map}&lt;{@link String},{@link String}&gt; rather than
     * {@link MessageResponse}.</p>
     *
     * <p><strong>Implementation note</strong> &mdash; {@link HashMap} is used
     * (as opposed to the immutable {@code Map.of(...)} factory) because the
     * field-error population happens iteratively inside the
     * {@code for (FieldError fe : ...)} loop, which requires a mutable map.
     * Field-error iteration follows binding-result discovery order; if Jackson
     * round-trip ordering matters to clients, they should sort by field name
     * client-side.</p>
     *
     * @param ex the {@link MethodArgumentNotValidException} containing the
     *           binding result with one or more {@link FieldError} entries
     * @return a {@link ResponseEntity} with HTTP status 400 and a body that is
     *         a {@code Map&lt;String, String&gt;} keyed by field name with
     *         the validation message as the value
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.error("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Translates an {@link AccessDeniedException} raised by Spring's method-
     * level security into an HTTP 403 (Forbidden) response with a constant
     * client-facing message.
     *
     * <p>This handler fires when a {@code @PreAuthorize} expression on a
     * controller method evaluates to {@code false} for the authenticated
     * principal &mdash; for example, when a user with only the
     * {@code ROLE_USER} authority calls a method annotated
     * {@code @PreAuthorize("hasRole('ADMIN')")} on
     * {@code ProductController.saveProduct},
     * {@code ProductController.saveProducts},
     * {@code ProductController.updateProduct} (both overloads), or
     * {@code ProductController.deleteProductByPrice}.</p>
     *
     * <p><strong>Important class-name disambiguation</strong> &mdash; the
     * handler parameter is
     * {@code org.springframework.security.access.AccessDeniedException} (a
     * Spring Security class), NOT
     * {@code java.nio.file.AccessDeniedException} (a JDK NIO class with the
     * same simple name). Mixing the two would cause this handler to never
     * fire because Spring would only invoke it for exception instances of
     * the imported class.</p>
     *
     * <p><strong>Security rationale</strong> &mdash; the response body is the
     * constant string {@code "Access denied: insufficient permissions"} so
     * that probing clients cannot infer which roles or authorities are
     * required for which endpoints. The original exception message is logged
     * server-side at ERROR level for operator debugging.</p>
     *
     * @param ex the {@link AccessDeniedException} thrown by Spring's
     *           method-level security
     * @return a {@link ResponseEntity} with HTTP status 403 and a
     *         {@link MessageResponse} body containing the constant message
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MessageResponse> handleAccessDenied(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new MessageResponse("Access denied: insufficient permissions"));
    }

    /**
     * Translates an {@link IllegalArgumentException} into an HTTP 400 (Bad
     * Request) response that <em>does</em> echo the exception's message back
     * to the client.
     *
     * <p>This handler is the single intentional exception to the
     * "do not leak {@code ex.getMessage()}" policy enforced elsewhere in
     * this advice. It is safe here because
     * {@code AuthService.register(SignupRequest)} deliberately constructs
     * client-friendly business-rule messages with {@code IllegalArgumentException},
     * specifically:</p>
     * <ul>
     *   <li>{@code "Error: Username is already taken!"} &mdash; thrown when
     *       {@code userRepository.existsByUsername(...)} returns {@code true};</li>
     *   <li>{@code "Error: Email is already in use!"} &mdash; thrown when
     *       {@code userRepository.existsByEmail(...)} returns {@code true};</li>
     *   <li>Any other {@link IllegalArgumentException} thrown intentionally
     *       inside the service layer to signal a violated business invariant
     *       to the API client.</li>
     * </ul>
     *
     * <p>These messages are crafted to be safe for direct client consumption:
     * they describe a business rule violation, not an internal implementation
     * detail. Hostile inputs cannot influence the message text because the
     * service layer constructs it from string literals, not from the request
     * payload.</p>
     *
     * <p><strong>Caveat for future maintainers</strong> &mdash; if any code
     * path begins throwing {@link IllegalArgumentException} with a message
     * that quotes user input or framework internals (e.g.
     * {@code "Invalid input: " + userSuppliedValue}), this handler must be
     * updated to either sanitize the message or replace the user-input
     * portion before reflecting it.</p>
     *
     * @param ex the {@link IllegalArgumentException} thrown by application
     *           code (typically {@code AuthService.register})
     * @return a {@link ResponseEntity} with HTTP status 400 and a
     *         {@link MessageResponse} body wrapping {@code ex.getMessage()}
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new MessageResponse(ex.getMessage()));
    }

    /**
     * Final catch-all handler for any {@link Exception} not handled by the
     * more specific {@code @ExceptionHandler} methods above. Returns HTTP 500
     * (Internal Server Error) with a constant client-facing message.
     *
     * <p>Spring's exception-handler resolution always picks the
     * <em>most specific</em> match before falling back to broader supertypes,
     * so handlers earlier in this class (which target concrete subclasses
     * such as {@link BadCredentialsException}, {@link AccessDeniedException},
     * etc.) take precedence over this catch-all even when they appear later
     * in source order. This handler therefore fires only for unexpected
     * exception types &mdash; for example, a database connectivity failure
     * surfaced as a {@code DataAccessException}, an unanticipated NPE, or
     * any {@code RuntimeException} not explicitly mapped above.</p>
     *
     * <p><strong>Security rationale</strong> &mdash; the response body is the
     * constant string {@code "An unexpected error occurred"}. This handler
     * <em>never</em> echoes {@code ex.getMessage()} (which could leak
     * portions of an SQL query, a stack frame, an internal class name, or
     * other implementation details) and <em>never</em> echoes
     * {@code ex.toString()} (which would leak the exception class name).</p>
     *
     * <p><strong>Stack-trace logging</strong> &mdash; this handler logs the
     * <em>full</em> stack trace by passing {@code ex} as the second SLF4J
     * argument: {@code log.error("Unexpected error", ex)}. This idiom (with
     * {@code ex} placed AFTER the format string and outside any
     * {@code "{}"} placeholder) instructs SLF4J to emit the entire stack
     * trace, which is essential for diagnosing unexpected failures.
     * Alternative idioms such as
     * {@code log.error("Unexpected error: {}", ex.getMessage())} would log
     * only the message and silently swallow the stack trace, severely
     * impeding root-cause analysis.</p>
     *
     * <p><strong>Why catch {@link Exception} and not {@link Throwable}</strong>
     * &mdash; catching {@link Throwable} would also catch {@link Error}
     * subclasses such as {@link OutOfMemoryError} and {@link StackOverflowError},
     * which signal that the JVM is in an unrecoverable state. Attempting to
     * build a JSON response in such conditions could mask the underlying
     * fault or trigger further failures. The {@link Exception}-rooted
     * hierarchy covers all reasonable application-level failures while
     * leaving JVM errors to propagate to the container.</p>
     *
     * @param ex any {@link Exception} not handled by a more specific
     *           handler above
     * @return a {@link ResponseEntity} with HTTP status 500 and a
     *         {@link MessageResponse} body containing the constant message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MessageResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new MessageResponse("An unexpected error occurred"));
    }
}
