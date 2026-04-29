package com.jspider.spring_boot_simple_crud_with_mysql.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

/**
 * Inbound Data Transfer Object (DTO) carrying the JSON body of a
 * {@code POST /api/auth/login} request from the API client to the
 * authentication subsystem.
 *
 * <p>This DTO is the binding API contract between HTTP clients (Postman,
 * curl, the Swagger UI, the {@code AuthControllerIntegrationTest} integration
 * suite) and the login pipeline implemented by
 * {@code AuthController#authenticateUser(LoginRequest)} &rarr;
 * {@code AuthService#authenticate(LoginRequest)}. Jackson deserialises an
 * inbound JSON object of the form:
 *
 * <pre>{@code
 *   {
 *     "username": "alice",
 *     "password": "secret123"
 *   }
 * }</pre>
 *
 * <p>directly into an instance of this class via the no-args constructor and
 * setters synthesised by Lombok {@link Data}. The instance is then passed to
 * {@code AuthService.authenticate}, which builds a
 * {@code UsernamePasswordAuthenticationToken} from {@link #getUsername()} and
 * {@link #getPassword()} and submits it to Spring Security's
 * {@code AuthenticationManager}. On a successful credential match the service
 * issues a JSON Web Token via {@code JwtUtils.generateJwtToken(Authentication)};
 * on failure Spring Security throws a {@code BadCredentialsException} that is
 * translated to an HTTP 401 by {@code GlobalExceptionHandler}.
 *
 * <p><strong>Validation contract</strong> &mdash; both fields are decorated
 * with the Jakarta Bean Validation 3.x {@link NotBlank} constraint, which
 * rejects {@code null}, empty strings, and whitespace-only strings. Validation
 * is enforced by Hibernate Validator 8.x (brought in by the
 * {@code spring-boot-starter-validation} starter, BOM-managed by Spring Boot
 * 3.5.x) and runs automatically because
 * {@code AuthController#authenticateUser} annotates the method parameter with
 * {@code @Valid}. A constraint violation triggers
 * {@code MethodArgumentNotValidException}, which the
 * {@code GlobalExceptionHandler} translates into an HTTP 400 response with a
 * field-error map.
 *
 * <p><strong>Why {@code @NotBlank} (and not {@code @NotNull} or
 * {@code @NotEmpty})</strong> &mdash; for credential fields the strictest
 * sensible policy is to refuse blank input outright. {@code @NotNull} would
 * permit an empty string {@code ""}; {@code @NotEmpty} would permit a
 * whitespace-only string {@code "   "}. {@code @NotBlank} rejects all three
 * cases, ensuring downstream code never sees a value that cannot logically
 * identify a user.
 *
 * <p><strong>No length constraint</strong> &mdash; deliberately, no
 * {@link jakarta.validation.constraints.Size} annotation is applied to either
 * field. Login is for <em>existing</em> credentials: a length-restricted
 * validation here would risk rejecting accounts whose username or password was
 * registered when no length constraint existed (or under a different policy).
 * The {@code SignupRequest} DTO enforces length bounds at registration time;
 * once an account exists, login validates only that the submitted credentials
 * are syntactically present.
 *
 * <p><strong>Security note &mdash; password log redaction</strong>: the
 * {@link #password} field carries the raw plaintext password sent by the
 * client over the wire. To prevent inadvertent credential leakage through
 * Spring's DEBUG-level request-binding logs (which call {@code toString()} on
 * bound arguments), the field is annotated with {@link ToString.Exclude} so
 * the Lombok-generated {@code toString} method omits it. The plaintext value
 * is still deserialised normally and made available to
 * {@code AuthService.authenticate} via the {@link #getPassword()} accessor,
 * after which it is consumed by {@code AuthenticationManager.authenticate}
 * (which delegates to {@code BCryptPasswordEncoder.matches} for verification
 * against the persisted BCrypt hash) and discarded with the request scope.
 * This redaction is mandated by AAP &sect;0.7.1.4 and is a non-negotiable
 * requirement.
 *
 * <p><strong>Lombok rationale</strong>:
 * <ul>
 *   <li>{@link Data} synthesises getter/setter pairs for each field, plus
 *       {@code equals}, {@code hashCode}, {@code toString}, and an implicit
 *       no-args constructor (Lombok's {@code @RequiredArgsConstructor}
 *       contribution to {@code @Data}, which produces a no-args constructor
 *       when no field is {@code final} or marked {@code @NonNull}). The
 *       no-args constructor is required by Jackson's default
 *       {@code BeanDeserializer} when binding inbound JSON; the setters are
 *       required by Jackson's property-based binding strategy.</li>
 *   <li>{@link ToString} is referenced via {@link ToString.Exclude} to
 *       suppress {@link #password} from the synthesised {@code toString}
 *       output. No further class-level {@code @ToString} configuration is
 *       required because {@link Data}'s contribution already invokes
 *       {@code @ToString} with default settings.</li>
 * </ul>
 *
 * <p>This class is intentionally a plain POJO: it carries no Spring stereotype
 * annotation ({@code @Component}, {@code @Service}, etc.) and no JPA mapping.
 * Each inbound request produces a fresh instance, making the class trivially
 * thread-safe by lack of shared mutable state. Fields are non-{@code final}
 * because Lombok {@link Data}'s no-args constructor (required by Jackson)
 * cannot coexist with {@code final} fields lacking initialisers.
 *
 * @see com.jspider.spring_boot_simple_crud_with_mysql.payload.request.SignupRequest
 *      for the registration-time DTO with additional fields
 *      ({@code email}, {@code role}) and length validation.
 * @see com.jspider.spring_boot_simple_crud_with_mysql.payload.response.JwtResponse
 *      for the outbound DTO returned on successful authentication.
 * @see com.jspider.spring_boot_simple_crud_with_mysql.payload.response.MessageResponse
 *      for the simpler outbound envelope used for error and status messages.
 */
@Data
public class LoginRequest {

    /**
     * The username submitted by the client to identify the account being
     * authenticated. Mirrors the {@code users.username} column on the
     * {@code User} entity. {@link NotBlank} rejects {@code null}, empty
     * strings, and whitespace-only strings; no {@link jakarta.validation.constraints.Size}
     * constraint is applied because login must accept whatever username the
     * account was originally registered under.
     */
    @NotBlank
    private String username;

    /**
     * The raw plaintext password submitted by the client. The value is
     * forwarded to {@code AuthService.authenticate}, which wraps it in a
     * {@code UsernamePasswordAuthenticationToken} and delegates verification
     * to Spring Security's {@code AuthenticationManager} (which in turn
     * invokes {@code BCryptPasswordEncoder.matches} against the persisted
     * BCrypt hash on the {@code users.password} column).
     *
     * <p>{@link NotBlank} rejects {@code null}, empty strings, and
     * whitespace-only strings. The {@link ToString.Exclude} annotation
     * removes this field from the {@code toString} output Lombok generates,
     * preventing inadvertent exposure of credentials in DEBUG-level Spring
     * MVC request-binding logs &mdash; a security-critical requirement per
     * AAP &sect;0.7.1.4.
     *
     * <p>No {@link jakarta.validation.constraints.Size} constraint is applied
     * because login must accept whatever password the account was originally
     * registered under, regardless of any subsequent change in registration
     * length policy.
     */
    @NotBlank
    @ToString.Exclude
    private String password;
}
