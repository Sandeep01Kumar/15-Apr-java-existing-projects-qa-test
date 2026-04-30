package com.jspider.spring_boot_simple_crud_with_mysql.payload.request;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * Inbound Data Transfer Object (DTO) carrying the JSON body of a
 * {@code POST /api/auth/register} request from the API client to the
 * authentication subsystem.
 *
 * <p>This DTO is the single binding contract between HTTP clients (Postman,
 * curl, the Swagger UI, the
 * {@code AuthControllerIntegrationTest} integration suite) and the registration
 * pipeline implemented by
 * {@code AuthController#registerUser(SignupRequest)} →
 * {@code AuthService#register(SignupRequest)}. Jackson deserialises an inbound
 * JSON object of the form:
 *
 * <pre>{@code
 *   {
 *     "username": "alice",
 *     "email":    "alice@example.com",
 *     "password": "secret123",
 *     "role":     ["admin"]
 *   }
 * }</pre>
 *
 * <p>directly into an instance of this class via the no-args constructor and
 * setters synthesised by Lombok {@link Data}. The {@code role} field is
 * <strong>optional</strong>; clients may omit it entirely to accept the
 * default {@code ROLE_USER} assignment performed by
 * {@code AuthService.register}.
 *
 * <p><strong>API contract decoupling</strong> &mdash; this DTO sits between
 * the HTTP wire and the JPA layer (the {@code User} and {@code Role}
 * entities and their backing role-name enum). It does <em>not</em> import
 * any persistence type. The {@code role} field is deliberately typed as
 * {@code Set<String>} (e.g. {@code "admin"}, {@code "user"}, anything else)
 * so that the API contract stays loose: clients are not required to know the
 * canonical enum names ({@code "ROLE_USER"}, {@code "ROLE_ADMIN"}). The
 * {@code AuthService.register} method performs the loose-string-to-enum
 * mapping (per AAP §0.5.1.6).
 *
 * <p><strong>Validation contract</strong> &mdash; field-level validation is
 * declared via Jakarta Bean Validation 3.x annotations and enforced by
 * Hibernate Validator 8.x (brought in by the
 * {@code spring-boot-starter-validation} starter, BOM-managed by Spring Boot
 * 3.5.x). The validation runs automatically because
 * {@code AuthController#registerUser} annotates the method parameter with
 * {@code @Valid}. A constraint violation triggers
 * {@code MethodArgumentNotValidException}, which the
 * {@code GlobalExceptionHandler} translates into an HTTP 400 response with a
 * field-error map. The constraints are:
 * <ul>
 *   <li>{@link #username} &mdash; {@link NotBlank} (rejects {@code null},
 *       empty, and whitespace-only) and {@link Size} with
 *       {@code min = 3, max = 20}. The 3-char minimum filters typically
 *       low-quality 1- and 2-char usernames; the 20-char maximum mirrors the
 *       {@code users.username} column constraint declared on the {@code User}
 *       entity.</li>
 *   <li>{@link #email} &mdash; {@link NotBlank}, {@link Size} with
 *       {@code max = 50} (mirroring {@code users.email}), and {@link Email}
 *       which performs a syntactic RFC 5322-compatible check. No explicit
 *       {@code min} length is declared because the {@code @Email} regex
 *       inherently requires at least the {@code a@b} form (3 characters).</li>
 *   <li>{@link #password} &mdash; {@link NotBlank} and {@link Size} with
 *       {@code min = 6, max = 40}. The 6-character lower bound establishes a
 *       minimal-strength baseline; the 40-character upper bound is well
 *       within BCrypt's 72-byte limit (BCrypt silently truncates inputs above
 *       72 bytes). The {@code users.password} column's {@code max = 120}
 *       constraint applies to the BCrypt <em>hash</em> stored after
 *       {@code BCryptPasswordEncoder#encode} &mdash; that is a different
 *       artefact governed by a different constraint than the raw password
 *       length checked here.</li>
 *   <li>{@link #role} &mdash; <strong>no validation annotations</strong> (and
 *       no {@code @NotNull}, {@code @NotEmpty}, or {@code @Size}) because the
 *       field is intentionally optional per the AAP. Adding a validation
 *       constraint would force every signup to specify a role, defeating the
 *       documented default-{@code ROLE_USER} behaviour in
 *       {@code AuthService.register}.</li>
 * </ul>
 *
 * <p><strong>Security note</strong> &mdash; the {@link #password} field
 * carries the raw plaintext password sent by the client over the wire. To
 * prevent inadvertent credential leakage through Spring's
 * DEBUG-level request-binding logs (which call {@code toString()} on bound
 * arguments), the field is annotated with {@link ToString.Exclude} so the
 * Lombok-generated {@code toString} method omits it. The plaintext value is
 * still deserialised normally and passed to
 * {@code BCryptPasswordEncoder.encode} inside
 * {@code AuthService.register}, after which it is replaced by the BCrypt
 * hash and never persisted in plaintext form.
 *
 * <p><strong>Lombok rationale</strong>:
 * <ul>
 *   <li>{@link Data} synthesises getter/setter pairs for each field, plus
 *       {@code equals}, {@code hashCode}, {@code toString}, and an implicit
 *       no-args constructor (Lombok's {@code @RequiredArgsConstructor}
 *       contribution to {@code @Data}, which produces a no-args constructor
 *       when no field is {@code final} or marked {@code @NonNull}). The
 *       no-args constructor is required by Jackson's default
 *       {@code BeanDeserializer} when binding inbound JSON.</li>
 *   <li>{@link ToString} is referenced via {@link ToString.Exclude} to
 *       suppress {@link #password} from the synthesised {@code toString}
 *       output. No further {@code @ToString} configuration is required at the
 *       class level because {@link Data}'s contribution already invokes
 *       {@code @ToString} with the default settings.</li>
 * </ul>
 *
 * <p>This class is intentionally a plain POJO: it carries no Spring stereotype
 * annotation ({@code @Component}, {@code @Service}, etc.) and no JPA
 * mapping. Each inbound request produces a fresh instance, making the class
 * trivially thread-safe by lack of shared mutable state.
 *
 * @see com.jspider.spring_boot_simple_crud_with_mysql.payload.request.LoginRequest
 *      for the simpler two-field credentials envelope used by the login
 *      endpoint.
 * @see com.jspider.spring_boot_simple_crud_with_mysql.payload.response.MessageResponse
 *      for the outbound DTO returned on successful registration.
 */
@Data
public class SignupRequest {

    /**
     * Desired unique username for the new account. Mirrors the
     * {@code users.username} column constraints declared on the {@code User}
     * entity. {@link NotBlank} rejects {@code null}, empty strings, and
     * whitespace-only strings; {@link Size} enforces a 3-20 character length
     * window appropriate for a public-facing identifier.
     */
    @NotBlank
    @Size(min = 3, max = 20)
    private String username;

    /**
     * E-mail address for the new account. Mirrors the {@code users.email}
     * column's {@link Size} constraint. {@link NotBlank} rejects null, empty,
     * and whitespace-only values; {@link Email} validates RFC 5322-style
     * syntax (Hibernate Validator's default email regex). No explicit
     * {@code min} length is declared because the email regex inherently
     * requires at least the {@code a@b} form.
     */
    @NotBlank
    @Size(max = 50)
    @Email
    private String email;

    /**
     * Raw plaintext password for the new account. This value is hashed via
     * {@code BCryptPasswordEncoder#encode} inside {@code AuthService.register}
     * before persistence; the plaintext is never written to the
     * {@code users.password} column or to any persistent log sink. The
     * {@link Size} bounds (6-40 characters) establish a usable minimum and
     * stay safely below BCrypt's 72-byte input ceiling. The
     * {@link ToString.Exclude} annotation removes this field from the
     * {@code toString} output Lombok generates, preventing inadvertent
     * exposure of credentials in DEBUG-level Spring MVC request-binding
     * logs.
     */
    @NotBlank
    @Size(min = 6, max = 40)
    @ToString.Exclude
    private String password;

    /**
     * Optional set of role names the client requests for the new account.
     * Accepts loose strings such as {@code "admin"}, {@code "user"}, or any
     * other token; the {@code AuthService.register} pipeline maps
     * {@code "admin"} (case-sensitive) to the {@code ROLE_ADMIN} enum
     * constant and any other value (or a {@code null} / empty {@link Set})
     * to the default {@code ROLE_USER} enum constant. The field carries no
     * validation annotations because absence is a legitimate input that
     * triggers the default-role pathway. A {@link Set} (rather than
     * {@code List}) is chosen so that Jackson silently de-duplicates
     * repeated values in the inbound JSON array, ensuring the service layer
     * sees each role at most once.
     */
    private Set<String> role;
}
