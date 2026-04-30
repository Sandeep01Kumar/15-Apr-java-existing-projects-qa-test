package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jspider.spring_boot_simple_crud_with_mysql.payload.request.LoginRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.request.SignupRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.response.JwtResponse;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.response.MessageResponse;
import com.jspider.spring_boot_simple_crud_with_mysql.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller exposing the two PUBLIC authentication endpoints required by
 * the JWT-based authentication and authorization feature added to the existing
 * {@code spring-boot-simple-crud-with-mysql} project.
 *
 * <p>This controller is the HTTP entry point for the JWT subsystem. It maps two
 * verbs onto the {@code /api/auth/**} URL space:
 * <ul>
 *   <li>{@code POST /api/auth/register} &mdash; create a new user account, hash
 *       the supplied password with BCrypt, attach the requested role set (or
 *       the default {@code ROLE_USER} when none is supplied), and persist the
 *       resulting {@code User} via Spring Data JPA. Handled by
 *       {@link #registerUser(SignupRequest)}.</li>
 *   <li>{@code POST /api/auth/login} &mdash; verify the supplied credentials
 *       through Spring Security's {@code AuthenticationManager}, mint a signed
 *       JSON Web Token via {@code JwtUtils}, and return the token together
 *       with the user's id, username, email, and role list. Handled by
 *       {@link #authenticateUser(LoginRequest)}.</li>
 * </ul>
 *
 * <h2>Public access posture</h2>
 *
 * <p>Per AAP &sect;0.5.1.5, the application's {@code SecurityConfig.filterChain}
 * declares {@code requestMatchers("/api/auth/**").permitAll()}, which makes
 * every endpoint hosted by this controller anonymously reachable. The
 * {@code /api/} prefix is mandatory: it is the exact pattern matched by the
 * filter-chain rule, and changing the class-level {@link RequestMapping} would
 * break the public access posture.
 *
 * <p>Because these endpoints are intentionally public, this controller
 * deliberately omits the
 * {@code @SecurityRequirement(name = "bearerAuth")} annotation (compare with
 * the existing {@code StudentController} and the modified
 * {@code ProductController}, both of which carry that annotation). Adding it
 * here would mislead OpenAPI consumers into believing that the registration
 * and login endpoints require a Bearer token, contradicting the
 * {@code permitAll()} rule and breaking the bootstrap flow that allows new
 * users to obtain their first token.
 *
 * <h2>Construction and dependency injection</h2>
 *
 * <p>The controller has a single collaborator, {@link AuthService}, which it
 * receives via Lombok-{@link RequiredArgsConstructor @RequiredArgsConstructor}-generated
 * constructor injection. This is the FIRST file in the project to use the
 * constructor-injection pattern (per AAP &sect;0.7.1.1, "Constructor injection
 * over field injection"), supplanting the {@code @Autowired}-field idiom of
 * the legacy {@link ProductController}. The benefits realised here are:
 * <ul>
 *   <li>The {@link AuthService} dependency is declared as
 *       {@code private final}, ensuring the reference is non-null and
 *       immutable once Spring's IoC container has wired the bean.</li>
 *   <li>The compile-time generated constructor lets unit tests instantiate
 *       the controller directly, without bootstrapping the Spring context, by
 *       passing a stub or Mockito mock for the service.</li>
 *   <li>Missing or ambiguous bean candidates surface as a startup
 *       {@code NoSuchBeanDefinitionException} or
 *       {@code NoUniqueBeanDefinitionException}, rather than as a NullPointer
 *       at first request.</li>
 * </ul>
 *
 * <h2>Request and response model</h2>
 *
 * <p>Both endpoints exchange JSON bodies using DTO types defined in the
 * {@code payload.request} and {@code payload.response} subpackages. Each
 * controller method declares its DTO parameter with both {@link RequestBody}
 * (so Jackson deserialises the JSON body into the DTO instance) and
 * {@link Valid} (so Hibernate Validator processes the
 * {@code @NotBlank}/{@code @Size}/{@code @Email} constraints declared on the
 * DTO fields before the method body executes). A constraint violation
 * triggers a {@code MethodArgumentNotValidException} that the application's
 * {@code GlobalExceptionHandler} translates into an HTTP 400 response with a
 * field-error map.
 *
 * <p>Per AAP &sect;0.5.1.6 critical rule #5 and AAP &sect;0.7.1.1 ("DTOs
 * strictly separate API from persistence"), the controller deliberately does
 * <em>not</em> use the project's legacy {@code ResponseStructure<T>} envelope.
 * That class is annotated {@code @Component} (Spring singleton) with
 * non-final, mutable fields, which makes it thread-unsafe under concurrent
 * request loads &mdash; a pre-existing bug documented in the AAP that this
 * new code studiously avoids by returning the response DTO directly inside a
 * {@link ResponseEntity}.
 *
 * <h2>CORS posture</h2>
 *
 * <p>The class-level
 * {@code @CrossOrigin(origins = "*", maxAge = 3600)} annotation mirrors the
 * permissive CORS intent of the existing {@code ProductController}. The
 * {@code maxAge = 3600} value (one hour) caches preflight {@code OPTIONS}
 * responses on browser clients, reducing the volume of preflight traffic
 * for a same-origin SPA. The {@code allowCredentials} flag is intentionally
 * left at its default of {@code false}: per AAP &sect;0.7.1.4, Spring Security
 * 6.x rejects {@code origins = "*"} with {@code allowCredentials = true} at
 * startup, and credentials in the cookie sense are not part of this
 * stateless JWT design (the bearer token rides the {@code Authorization}
 * header rather than a cookie).
 *
 * <h2>Exception strategy</h2>
 *
 * <p>The controller methods deliberately omit {@code try}/{@code catch}
 * blocks and let every exception propagate up to the application-wide
 * {@code @RestControllerAdvice} (i.e. {@code GlobalExceptionHandler}). This
 * follows AAP &sect;0.5.1.6 critical rule #8 and yields three concrete
 * benefits:
 * <ul>
 *   <li>The error-translation logic lives in exactly one place, so changes
 *       to error response shape, status code, or logging propagate
 *       consistently across the entire authentication surface.</li>
 *   <li>The controller stays focused on its core responsibility &mdash; the
 *       HTTP-to-service binding &mdash; without leaking knowledge of specific
 *       service-layer exception types.</li>
 *   <li>Exceptions thrown by the {@link AuthService} (e.g.
 *       {@code IllegalArgumentException} for duplicate username/email,
 *       {@code BadCredentialsException} for invalid login credentials,
 *       {@code MethodArgumentNotValidException} for DTO validation failures)
 *       map to their canonical HTTP status codes (400, 401, 400 respectively)
 *       inside {@code GlobalExceptionHandler}, providing a uniform error
 *       contract across the entire API.</li>
 * </ul>
 *
 * <h2>OpenAPI integration</h2>
 *
 * <p>The class-level {@link Tag @Tag} groups both endpoints under the
 * "Authentication" heading in the auto-generated Swagger UI hosted by
 * {@code springdoc-openapi-starter-webmvc-ui:2.8.6}. The per-method
 * {@link Operation @Operation} annotations supply the human-readable summary
 * shown in the endpoint listing. Together with the absence of a class-level
 * {@code @SecurityRequirement}, these annotations cause the Swagger UI to
 * render both endpoints with no padlock icon, accurately reflecting their
 * public-access posture.
 *
 * <h2>Thread safety</h2>
 *
 * <p>This controller is a Spring singleton: a single instance is shared by
 * every request thread that hits either endpoint. Thread safety is
 * structurally guaranteed because the only field is {@code private final}
 * and initialised exactly once at construction by the Lombok-generated
 * constructor; no instance state is ever mutated after construction. Method
 * locals reside on the calling thread's stack, and the collaborating
 * {@link AuthService} bean is itself documented as concurrent-safe.
 *
 * <h2>Out-of-scope endpoints</h2>
 *
 * <p>Per AAP &sect;0.6.2, the following authentication-adjacent endpoints
 * are explicitly NOT exposed by this controller and are not implemented in
 * the project at all: refresh-token issuance, logout, password reset, email
 * verification, change-password, profile/{@code me}/{@code whoami} lookups,
 * user listing, and user deletion. Adding any of them would require a
 * separate change request that updates the controller, the service, the
 * security configuration, the integration tests, and the OpenAPI documentation
 * in concert.
 *
 * @see AuthService
 * @see SignupRequest
 * @see LoginRequest
 * @see JwtResponse
 * @see MessageResponse
 * @see com.jspider.spring_boot_simple_crud_with_mysql.security.SecurityConfig
 * @see com.jspider.spring_boot_simple_crud_with_mysql.exception.GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Authentication", description = "User registration and login endpoints")
@RequiredArgsConstructor
public class AuthController {

    /**
     * Authentication service collaborator that orchestrates the registration
     * and login flows. Injected via Lombok-{@link RequiredArgsConstructor}-generated
     * constructor injection; the {@code private final} qualifier guarantees
     * that the field is initialised exactly once at controller construction
     * and never replaced thereafter. The Spring IoC container resolves the
     * bean by type at startup, locating the unique
     * {@code @Service}-annotated implementation.
     *
     * <p>The controller delegates all authentication-related business logic
     * (uniqueness checks, password hashing, role resolution, credential
     * verification, JWT issuance) to this service rather than duplicating it
     * inline; the controller's role is purely the HTTP binding layer. This
     * is the only collaborator the controller needs &mdash; per AAP
     * &sect;0.5.1.6 critical rule #14, no other beans (such as
     * {@code JwtUtils}, {@code UserRepository}, or {@code PasswordEncoder})
     * are injected here.
     */
    private final AuthService authService;

    /**
     * Handles a {@code POST /api/auth/register} request to create a new user
     * account.
     *
     * <p>The method delegates the registration work to
     * {@link AuthService#register(SignupRequest)} and wraps the resulting
     * {@link MessageResponse} in an HTTP 200 response. The bean validation
     * triggered by the {@link Valid @Valid} annotation runs <em>before</em>
     * this method body executes; if any
     * {@code @NotBlank}/{@code @Size}/{@code @Email} constraint declared on
     * {@link SignupRequest} fails, Spring throws
     * {@code MethodArgumentNotValidException} and the
     * {@code GlobalExceptionHandler} renders an HTTP 400 response with a
     * field-error map.
     *
     * <p>The service may also throw an {@link IllegalArgumentException} when
     * the supplied username or email is already taken, or when an explicit
     * role lookup fails (e.g. the seed bean has not yet populated the
     * {@code roles} table). Per AAP &sect;0.5.1.6 critical rule #4 and AAP
     * &sect;0.5.1.6 critical rule #8, these exceptions are <em>not</em>
     * caught locally; they propagate to {@code GlobalExceptionHandler}, which
     * translates them into HTTP 400 with the exception's message in the
     * response body.
     *
     * <p>The endpoint is publicly accessible: the
     * {@code SecurityConfig.filterChain} declares
     * {@code requestMatchers("/api/auth/**").permitAll()}, so the request
     * succeeds even when the inbound HTTP request carries no
     * {@code Authorization} header.
     *
     * <p>Example request body:
     * <pre>{@code
     *   {
     *     "username": "alice",
     *     "email":    "alice@example.com",
     *     "password": "secret123"
     *   }
     * }</pre>
     *
     * <p>Example success response (HTTP 200):
     * <pre>{@code
     *   {"message":"User registered successfully!"}
     * }</pre>
     *
     * @param signupRequest the validated registration payload deserialised
     *                      from the inbound JSON body; the {@link Valid @Valid}
     *                      annotation guarantees that its {@code username},
     *                      {@code email}, and {@code password} fields are
     *                      non-blank and within their declared length bounds
     *                      before this method body runs
     * @return an HTTP 200 {@link ResponseEntity} carrying a
     *         {@link MessageResponse} with the static success message
     *         {@code "User registered successfully!"}
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody SignupRequest signupRequest) {
        return ResponseEntity.ok(authService.register(signupRequest));
    }

    /**
     * Handles a {@code POST /api/auth/login} request to authenticate an
     * existing user and issue a signed JSON Web Token.
     *
     * <p>The method delegates the credential verification and token-issuance
     * work to {@link AuthService#authenticate(LoginRequest)} and wraps the
     * resulting {@link JwtResponse} in an HTTP 200 response. Bean validation
     * triggered by {@link Valid @Valid} runs first: a {@code @NotBlank}
     * violation on {@code username} or {@code password} produces an HTTP 400
     * via the {@code GlobalExceptionHandler}, exactly as for the
     * registration endpoint.
     *
     * <p>The service surfaces a {@code BadCredentialsException} (thrown by
     * Spring Security's {@code AuthenticationManager}) for both invalid
     * passwords and unknown usernames &mdash; Spring Security's
     * {@code DaoAuthenticationProvider.hideUserNotFoundExceptions} setting
     * is left at its default of {@code true}, which collapses the two
     * failure modes into the same exception type to defeat user-enumeration
     * attacks. Per AAP &sect;0.5.1.6 critical rule #8, this exception is
     * <em>not</em> caught locally; it propagates to
     * {@code GlobalExceptionHandler}, which renders an HTTP 401 response
     * with the deliberately generic message
     * {@code "Invalid username or password"}.
     *
     * <p>The endpoint is publicly accessible. The {@code @Valid} validation
     * runs on every request, but no authentication is required to reach the
     * handler &mdash; that would be a chicken-and-egg problem since this is
     * the endpoint clients call to <em>obtain</em> a token.
     *
     * <p>Example request body:
     * <pre>{@code
     *   {
     *     "username": "alice",
     *     "password": "secret123"
     *   }
     * }</pre>
     *
     * <p>Example success response (HTTP 200):
     * <pre>{@code
     *   {
     *     "token":    "eyJhbGciOiJIUzI1NiJ9...",
     *     "type":     "Bearer",
     *     "id":       42,
     *     "username": "alice",
     *     "email":    "alice@example.com",
     *     "roles":    ["ROLE_USER"]
     *   }
     * }</pre>
     *
     * <p>The returned {@code token} should be presented on every subsequent
     * protected request via the {@code Authorization: Bearer &lt;token&gt;}
     * header.
     *
     * @param loginRequest the validated login payload deserialised from the
     *                     inbound JSON body; {@link Valid @Valid} guarantees
     *                     that {@code username} and {@code password} are both
     *                     non-blank before this method body runs
     * @return an HTTP 200 {@link ResponseEntity} carrying a {@link JwtResponse}
     *         with the freshly-signed JWT, the {@code "Bearer"} type, the
     *         authenticated user's id, username, email, and a list of role
     *         names with the {@code ROLE_} prefix preserved
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a JWT")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.authenticate(loginRequest));
    }
}
