package com.jspider.spring_boot_simple_crud_with_mysql.payload.response;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound Data Transfer Object (DTO) returned by
 * {@code POST /api/auth/login} after a successful credential exchange.
 *
 * <p>This envelope conveys the six pieces of information the API client needs
 * after authenticating: the signed JSON Web Token, the bearer-scheme prefix
 * ({@code "Bearer"}), and a small set of user-display attributes
 * ({@code id}, {@code username}, {@code email}, {@code roles}). Jackson
 * serialises an instance to a JSON object of the form:
 *
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
 * <p>The DTO strictly separates the API response contract from the JPA
 * entity layer ({@code User}, {@code Role}). In particular, the BCrypt
 * password hash held by {@code User.password} is never carried by this
 * class &mdash; no {@code password} field exists, so accidental serialisation
 * of credentials is structurally impossible.
 *
 * <p><strong>Producers</strong> &mdash; the following components instantiate
 * {@code JwtResponse} for outbound responses:
 * <ul>
 *   <li>{@code AuthService.authenticate(LoginRequest)} constructs an instance
 *       via the explicit five-argument constructor
 *       {@link #JwtResponse(String, Long, String, String, List)} after
 *       producing a signed token through {@code JwtUtils.generateJwtToken}.
 *       The callsite passes
 *       {@code new JwtResponse(token, userDetails.getId(),
 *       userDetails.getUsername(), userDetails.getEmail(), roleNames)} and
 *       relies on the {@link #type} field initializer to default to
 *       {@code "Bearer"}.</li>
 *   <li>{@code AuthController#authenticateUser(LoginRequest)} wraps the
 *       service result in {@link org.springframework.http.ResponseEntity#ok}
 *       and returns it as the HTTP 200 body.</li>
 * </ul>
 *
 * <p><strong>Consumers</strong> &mdash; HTTP clients (Postman, curl, Swagger
 * UI, the Spring Boot integration tests under
 * {@code src/test/java/.../controller/AuthControllerIntegrationTest}) read
 * the six fields from the JSON response. Jackson deserialisation in tests
 * relies on the no-args constructor synthesised by Lombok {@link Data}
 * combined with the {@code Data}-generated setters.
 *
 * <p><strong>Field semantics</strong>:
 * <ul>
 *   <li>{@link #token} &mdash; the signed JWS (compact-serialisation form),
 *       valid for the duration configured by {@code jwt.expiration} in
 *       {@code application.properties}. Clients must echo this string back on
 *       subsequent requests via the {@code Authorization: Bearer &lt;token&gt;}
 *       header.</li>
 *   <li>{@link #type} &mdash; the bearer-scheme prefix the client should
 *       prepend to the token (with a single space) when constructing the
 *       {@code Authorization} header. The literal {@code "Bearer"} default is
 *       hard-coded in the field initializer and preserved by every
 *       construction path because the explicit constructor deliberately omits
 *       a {@code type} parameter.</li>
 *   <li>{@link #id} &mdash; the database primary key of the authenticated
 *       user (the {@code users.id} column populated by
 *       {@code @GeneratedValue(strategy = GenerationType.IDENTITY)} on the
 *       {@code User} entity). Boxed {@link Long} (rather than primitive
 *       {@code long}) preserves null-safety for partial-response edge cases
 *       and aligns with the {@code User.id} field type.</li>
 *   <li>{@link #username} &mdash; the {@code users.username} value supplied
 *       at registration time and used as the JWT subject ({@code sub}).</li>
 *   <li>{@link #email} &mdash; the {@code users.email} value, returned only
 *       in the login response payload. Notably it is <em>not</em> embedded in
 *       the JWT itself, which keeps PII out of the bearer token while still
 *       making the e-mail available to the client UI for profile display.</li>
 *   <li>{@link #roles} &mdash; the user's role names as plain strings
 *       (e.g. {@code "ROLE_USER"}, {@code "ROLE_ADMIN"}). The list type
 *       (rather than a set) yields deterministic JSON ordering when Jackson
 *       serialises the response, which keeps integration-test assertions
 *       stable.</li>
 * </ul>
 *
 * <p><strong>Lombok rationale</strong>:
 * <ul>
 *   <li>{@link Data} synthesises getter/setter pairs, {@code equals},
 *       {@code hashCode}, and {@code toString}, mirroring the Lombok-driven
 *       convention established by the existing
 *       {@code com.jspider.spring_boot_simple_crud_with_mysql.entity.Product}
 *       and {@code ...responses.ResponseStructure} classes.</li>
 *   <li>{@link NoArgsConstructor} is applied <em>explicitly</em> because
 *       Lombok 1.18.46's {@code @Data} (which expands to
 *       {@code @RequiredArgsConstructor}) skips constructor generation
 *       whenever an explicit constructor of any signature is declared on
 *       the class &mdash; an empirical limitation verified against the
 *       running Maven build (compiled bytecode of {@code JwtResponse.class}
 *       contained only the explicit five-argument constructor and lacked a
 *       zero-arg {@code <init>()} member when {@code @NoArgsConstructor}
 *       was omitted, causing Jackson to raise
 *       {@code InvalidDefinitionException: no Creators, like default
 *       constructor, exist} during deserialisation). The explicit
 *       {@code @NoArgsConstructor} therefore restores the public
 *       zero-parameter constructor that Jackson requires for property-based
 *       deserialisation in {@code AuthControllerIntegrationTest}, and the
 *       schema-required {@code JwtResponse()} member is preserved in the
 *       compiled class.</li>
 *   <li>The explicit five-argument constructor coexists legally with the
 *       Lombok-generated no-args constructor; the two signatures are
 *       distinct, so neither suppresses the other. The explicit constructor
 *       serves production callers ({@code AuthService.authenticate}) while
 *       the no-args constructor serves Jackson deserialisation in
 *       integration tests.</li>
 *   <li>The explicit five-argument constructor deliberately omits a
 *       {@code type} parameter so that the {@code "Bearer"} field
 *       initializer is preserved in every successful login response without
 *       forcing every caller to pass the prefix literal.</li>
 * </ul>
 *
 * <p>This class is intentionally a plain POJO: it carries no Spring stereotype
 * annotation ({@code @Component}, {@code @Service}, etc.) and no JPA mapping.
 * Each authenticated request produces a fresh instance, making the class
 * trivially thread-safe by lack of shared mutable state.
 *
 * @see com.jspider.spring_boot_simple_crud_with_mysql.payload.response.MessageResponse
 *      for the simpler single-field message envelope used by registration
 *      success and global exception responses.
 */
@Data
@NoArgsConstructor
public class JwtResponse {

    /**
     * The signed JSON Web Token (compact-serialisation, three Base64URL
     * segments separated by dots) that the client must echo back on
     * subsequent requests via the {@code Authorization} header. Issued by
     * {@code JwtUtils.generateJwtToken(Authentication)} using HMAC-SHA-256
     * with the application-configured secret.
     */
    private String token;

    /**
     * Bearer-scheme prefix the client should combine with {@link #token}
     * when constructing the {@code Authorization} header. Always
     * {@code "Bearer"}; the value is hard-coded as a field initializer so
     * that every successful login carries the prefix even when callers
     * invoke the five-argument constructor (which, by design, does not
     * accept a {@code type} parameter).
     */
    private String type = "Bearer";

    /**
     * Database primary key of the authenticated user, sourced from the
     * {@code users.id} column. Boxed {@link Long} preserves null-safety for
     * partial-response scenarios (test fixtures, future enhancements).
     */
    private Long id;

    /**
     * The authenticated user's unique username, which also doubles as the
     * subject ({@code sub}) claim of the issued {@link #token}.
     */
    private String username;

    /**
     * The authenticated user's e-mail address. Returned in the login
     * response payload for client display, but deliberately not embedded in
     * the JWT to minimise PII exposure in tokens that may be cached or
     * logged downstream.
     */
    private String email;

    /**
     * Role names granted to the authenticated user, expressed as plain
     * strings with the {@code ROLE_} prefix preserved (e.g.
     * {@code ["ROLE_USER"]}, {@code ["ROLE_USER", "ROLE_ADMIN"]}). A
     * {@link List} type is chosen over {@code Set} so that Jackson
     * serialisation produces a deterministic ordering for stable integration
     * test assertions.
     */
    private List<String> roles;

    /**
     * Builds a fully-populated login response. The {@link #type} field is
     * intentionally absent from the parameter list so its
     * {@code "Bearer"} field-initializer default is always preserved,
     * keeping production callsites concise (e.g.
     * {@code new JwtResponse(jwt, user.getId(), user.getUsername(),
     * user.getEmail(), roleNames)}).
     *
     * @param accessToken the signed JWS to be returned to the client and
     *                    assigned to {@link #token}; must not be {@code null}
     *                    in production flows
     * @param id          the database primary key of the authenticated user;
     *                    boxed to allow {@code null} in test fixtures
     * @param username    the authenticated user's username
     * @param email       the authenticated user's e-mail address
     * @param roles       the role-name strings (with {@code ROLE_} prefix)
     *                    granted to the user; the caller-supplied
     *                    {@link List} reference is stored directly without
     *                    defensive copy, consistent with the surrounding
     *                    Lombok {@code @Data} POJO conventions
     */
    public JwtResponse(String accessToken, Long id, String username, String email, List<String> roles) {
        this.token = accessToken;
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles;
    }
}
