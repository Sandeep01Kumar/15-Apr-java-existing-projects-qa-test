package com.jspider.spring_boot_simple_crud_with_mysql.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.ERole;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.Role;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.request.LoginRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.request.SignupRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.response.JwtResponse;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.response.MessageResponse;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.RoleRepository;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.UserRepository;
import com.jspider.spring_boot_simple_crud_with_mysql.security.jwt.JwtUtils;
import com.jspider.spring_boot_simple_crud_with_mysql.security.services.UserDetailsImpl;

import lombok.RequiredArgsConstructor;

/**
 * Application service that orchestrates the JWT-based authentication and
 * registration flows for the public {@code /api/auth/**} endpoints.
 *
 * <p>This class is the single point of coordination between the four
 * collaborating subsystems introduced by the JWT authentication and
 * authorization feature (per AAP &sect;0.5.1.6):
 *
 * <ul>
 *   <li><strong>Persistence layer</strong> &mdash; through the injected
 *       {@link UserRepository} and {@link RoleRepository}, this service
 *       performs username/email uniqueness checks, role lookups, and the
 *       actual {@code INSERT} into the {@code users} and {@code user_roles}
 *       tables on a successful registration.</li>
 *   <li><strong>Security infrastructure</strong> &mdash; through the injected
 *       {@link PasswordEncoder} (a {@code BCryptPasswordEncoder} bean defined
 *       on {@code SecurityConfig}) and {@link AuthenticationManager} (also
 *       bean-supplied by {@code SecurityConfig}), this service hashes inbound
 *       passwords for storage and delegates credential verification to the
 *       Spring Security {@code DaoAuthenticationProvider} during login.</li>
 *   <li><strong>JWT subsystem</strong> &mdash; through the injected
 *       {@link JwtUtils} component, this service mints the signed access
 *       token returned to clients on successful authentication.</li>
 *   <li><strong>API DTO layer</strong> &mdash; through the
 *       {@link SignupRequest}, {@link LoginRequest}, {@link JwtResponse}, and
 *       {@link MessageResponse} types, this service stays agnostic of HTTP
 *       concerns and exposes a pure Java API to its sole controller-layer
 *       consumer, {@code AuthController}.</li>
 * </ul>
 *
 * <h2>Public surface</h2>
 *
 * <p>The class deliberately exposes <em>only</em> two public methods, in
 * keeping with the AAP's tightly scoped contract:
 * <ol>
 *   <li>{@link #register(SignupRequest)} &mdash; validates and persists a new
 *       user account, assigning either the default {@code ROLE_USER} or an
 *       explicitly requested role set.</li>
 *   <li>{@link #authenticate(LoginRequest)} &mdash; authenticates an
 *       existing account against the persisted BCrypt hash, mints a JWT,
 *       and returns the token alongside enough user metadata for the client
 *       to display a welcome surface.</li>
 * </ol>
 *
 * <p>Out-of-scope methods (per AAP &sect;0.6.2) intentionally absent from
 * this class: refresh-token issuance, password reset, email verification,
 * change-password flows, account deactivation, two-factor enrollment, role
 * administration, user listing/deletion, and any arithmetic, mailing, or
 * audit-trail concerns. Adding any such surface would require a separate
 * change request that updates the service, controller, integration tests,
 * and OpenAPI documentation in concert.
 *
 * <h2>Construction and dependency injection</h2>
 *
 * <p>The class is annotated with Spring's {@link Service @Service} stereotype
 * so that {@code @ComponentScan} on the bootstrap class registers it as a
 * Spring-managed singleton bean named {@code authService} at application
 * startup. Lombok's {@link RequiredArgsConstructor @RequiredArgsConstructor}
 * generates the all-{@code final}-fields constructor that Spring uses for
 * constructor injection &mdash; the recommended Spring 6.x pattern for new
 * code per AAP &sect;0.7.1.1, supplanting field injection with
 * {@code @Autowired} on the legacy {@code ProductController}. The five
 * dependencies are resolved as follows:
 *
 * <ul>
 *   <li>{@code userRepository} &mdash; supplied by Spring Data JPA's
 *       {@link UserRepository} proxy.</li>
 *   <li>{@code roleRepository} &mdash; supplied by Spring Data JPA's
 *       {@link RoleRepository} proxy.</li>
 *   <li>{@code encoder} &mdash; supplied by the
 *       {@code SecurityConfig.passwordEncoder()} bean
 *       ({@code BCryptPasswordEncoder} with the framework default strength
 *       of 10).</li>
 *   <li>{@code authenticationManager} &mdash; supplied by the
 *       {@code SecurityConfig.authenticationManager(AuthenticationConfiguration)}
 *       bean, which exposes the
 *       {@code DaoAuthenticationProvider}-driven {@code AuthenticationManager}
 *       wired with {@code UserDetailsServiceImpl} and the same
 *       {@code BCryptPasswordEncoder}.</li>
 *   <li>{@code jwtUtils} &mdash; supplied by the {@link JwtUtils @Component}
 *       managed by Spring's component scan.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 *
 * <p>This class is a Spring singleton: a single instance is shared by every
 * request thread that lands on either {@code AuthController} endpoint.
 * Thread safety is structurally guaranteed by three properties:
 *
 * <ul>
 *   <li>All fields are {@code private final} and initialized exactly once by
 *       the Lombok-generated constructor; no instance state is ever
 *       mutated after construction.</li>
 *   <li>Both public methods are stateless: every local variable lives on
 *       the calling thread's stack, no static or instance fields are
 *       written, and the only shared mutable surface
 *       ({@link SecurityContextHolder}) uses a thread-local strategy by
 *       default, so per-thread updates do not leak between requests.</li>
 *   <li>The collaborators are themselves thread-safe Spring beans:
 *       Spring Data repositories synthesise stateless proxies, the
 *       {@code BCryptPasswordEncoder} stores no mutable state, the
 *       {@code AuthenticationManager} delegates to a stateless provider
 *       chain, and {@link JwtUtils} is documented as concurrent-safe.</li>
 * </ul>
 *
 * <h2>Transactional semantics</h2>
 *
 * <p>The service deliberately omits an {@code @Transactional} annotation on
 * either method (per AAP &sect;0.7.1.1 and AAP &sect;0.5.1.6 critical rule
 * #8). Spring Data JPA's
 * {@link org.springframework.data.jpa.repository.JpaRepository#save(Object)}
 * runs inside its own implicit {@code REQUIRED} transaction, which is
 * sufficient for the registration flow's single
 * {@code INSERT INTO users / INSERT INTO user_roles} cascade. The
 * authentication flow performs only read-only repository access (via
 * {@code UserDetailsServiceImpl.loadUserByUsername} inside the
 * {@code AuthenticationManager}) and does not require an outer transaction.
 *
 * <h2>Exception strategy</h2>
 *
 * <p>The service relies on the application-wide
 * {@code GlobalExceptionHandler @RestControllerAdvice} to translate
 * service-layer exceptions into HTTP responses, rather than catching
 * exceptions locally and constructing error envelopes itself. The mapping
 * is:
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} (thrown for duplicate username,
 *       duplicate email, and missing role lookups) &rarr; HTTP 400 with the
 *       exception message in the response body.</li>
 *   <li>{@code BadCredentialsException} (thrown by the
 *       {@code AuthenticationManager} when login credentials are invalid)
 *       &rarr; HTTP 401 with the generic message
 *       {@code "Invalid username or password"} (deliberately
 *       enumeration-resistant).</li>
 *   <li>{@code UsernameNotFoundException} (thrown by
 *       {@code UserDetailsServiceImpl} when the username is unknown) is
 *       transparently rewrapped by Spring Security's
 *       {@code DaoAuthenticationProvider} into a
 *       {@code BadCredentialsException}, taking the same translation path as
 *       wrong-password failures.</li>
 * </ul>
 *
 * <p>Per AAP &sect;0.5.1.6 critical rule #5, the service explicitly does
 * <strong>not</strong> catch {@code BadCredentialsException} in
 * {@link #authenticate(LoginRequest)}; doing so would either require
 * re-throwing (redundant), translating to a custom exception (unnecessary
 * complexity), or returning a sentinel value (violation of the contract that
 * a successful return implies a valid {@link JwtResponse}).
 *
 * @see AuthenticationManager
 * @see PasswordEncoder
 * @see JwtUtils
 * @see UserDetailsImpl
 * @see com.jspider.spring_boot_simple_crud_with_mysql.security.SecurityConfig
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Repository facade for the {@link User} entity. Used in
     * {@link #register(SignupRequest)} to issue the
     * {@link UserRepository#existsByUsername(String) existsByUsername} and
     * {@link UserRepository#existsByEmail(String) existsByEmail} duplicate
     * checks before the new account is created, and to persist the
     * fully-assembled {@link User} (with its associated role set) via the
     * inherited {@code save} method.
     *
     * <p>The {@code authenticate} flow does not consult this repository
     * directly &mdash; user lookup during login is performed inside Spring
     * Security's {@code DaoAuthenticationProvider}, which delegates to
     * {@code UserDetailsServiceImpl}, which itself depends on this same
     * repository bean. A second lookup here would be a redundant database
     * round-trip.
     */
    private final UserRepository userRepository;

    /**
     * Repository facade for the {@link Role} entity. Used in
     * {@link #register(SignupRequest)} to resolve each requested role name
     * (or the default {@link ERole#ROLE_USER} when none is requested) to a
     * managed {@link Role} entity that can be attached to the new user via
     * the {@code user_roles} join table. The lookup uses
     * {@link RoleRepository#findByName(ERole)} and treats an empty
     * {@link java.util.Optional} as a hard configuration error, throwing
     * {@link IllegalArgumentException} that the
     * {@code GlobalExceptionHandler} renders as HTTP 400.
     *
     * <p>The repository is populated at application startup by the
     * {@code roleSeeder} {@code ApplicationRunner} bean defined on the
     * application's bootstrap class; in correctly configured deployments
     * every {@link ERole} constant maps to exactly one row, and
     * {@code findByName} never returns an empty {@code Optional} during
     * normal operation.
     */
    private final RoleRepository roleRepository;

    /**
     * BCrypt-backed password encoder, supplied by the
     * {@code SecurityConfig.passwordEncoder()} {@code @Bean}. Used in
     * {@link #register(SignupRequest)} to convert the inbound plain-text
     * password into the BCrypt hash that is persisted on the
     * {@code users.password} column.
     *
     * <p>The same bean is also wired into Spring Security's
     * {@code DaoAuthenticationProvider} so that
     * {@link AuthenticationManager#authenticate(Authentication)} can verify
     * inbound login credentials against the stored hash via
     * {@link PasswordEncoder#matches(CharSequence, String)}. Sharing one
     * encoder bean across both flows guarantees that the hash format used
     * for storage matches the format used for verification.
     *
     * <p>The field is named {@code encoder} (rather than the more verbose
     * {@code passwordEncoder}) per AAP &sect;0.5.1.6 to keep callsite syntax
     * concise (e.g. {@code encoder.encode(rawPassword)}).
     */
    private final PasswordEncoder encoder;

    /**
     * Spring Security's authentication manager, supplied by the
     * {@code SecurityConfig.authenticationManager(AuthenticationConfiguration)}
     * {@code @Bean}. Used in {@link #authenticate(LoginRequest)} to verify
     * inbound credentials by submitting a freshly-built
     * {@link UsernamePasswordAuthenticationToken}; on success the manager
     * returns a fully-authenticated {@link Authentication} whose
     * {@code principal} is a {@link UserDetailsImpl}.
     *
     * <p>Internally the manager delegates to a
     * {@code DaoAuthenticationProvider} configured with
     * {@code UserDetailsServiceImpl} and the same {@link #encoder} bean. The
     * provider loads the user by username, compares the BCrypt hash, and
     * either returns the authenticated token or throws a
     * {@code BadCredentialsException} that propagates up the call chain to
     * {@code GlobalExceptionHandler.handleBadCredentials}.
     */
    private final AuthenticationManager authenticationManager;

    /**
     * Component encapsulating every interaction with the JJWT library.
     * Used in {@link #authenticate(LoginRequest)} to mint a freshly-signed
     * JWT for the authenticated user via
     * {@link JwtUtils#generateJwtToken(Authentication)}.
     *
     * <p>The signed token carries only the three RFC 7519 registered claims
     * ({@code sub}, {@code iat}, {@code exp}) and is keyed off the
     * {@code jwt.secret} property. {@code AuthService} is the only producer
     * of tokens in the application; the consumer is
     * {@code AuthTokenFilter} on every protected request.
     */
    private final JwtUtils jwtUtils;

    /**
     * Registers a new user account based on the validated
     * {@link SignupRequest} payload received by {@code AuthController}.
     *
     * <p>The method executes the following sequence (the order is significant
     * per AAP &sect;0.5.1.6 critical rule #11):
     *
     * <ol>
     *   <li><strong>Username uniqueness check</strong> &mdash; calls
     *       {@link UserRepository#existsByUsername(String)} wrapped in
     *       {@code Boolean.TRUE.equals(...)} for null-safety. A {@code true}
     *       result throws {@link IllegalArgumentException} with the message
     *       {@code "Error: Username is already taken!"}, which the
     *       {@code GlobalExceptionHandler} translates into HTTP 400.</li>
     *   <li><strong>Email uniqueness check</strong> &mdash; calls
     *       {@link UserRepository#existsByEmail(String)} with the same
     *       null-safe pattern. A {@code true} result throws
     *       {@link IllegalArgumentException} with the message
     *       {@code "Error: Email is already in use!"}.</li>
     *   <li><strong>Password hashing</strong> &mdash; invokes
     *       {@link PasswordEncoder#encode(CharSequence)} to convert the
     *       plain-text password into a BCrypt hash before constructing the
     *       {@link User} entity. Order matters: the two existence checks run
     *       first so that a duplicate registration does not waste the
     *       BCrypt computation (which is the slowest step in this flow at
     *       roughly 100 ms).</li>
     *   <li><strong>User construction</strong> &mdash; instantiates a fresh
     *       {@link User} via the convenience three-argument constructor
     *       {@code User(String username, String email, String password)},
     *       passing the hashed password rather than the plain-text input.</li>
     *   <li><strong>Role resolution</strong> &mdash; if the request's
     *       {@link SignupRequest#getRole() role} field is {@code null} or
     *       empty, a single {@link ERole#ROLE_USER} is resolved as the
     *       default. Otherwise, every requested role string is mapped
     *       case-insensitively: the literal {@code "admin"} (in any case)
     *       resolves to {@link ERole#ROLE_ADMIN}; <em>any other string</em>
     *       (including unknown role names, the literal {@code "user"}, the
     *       empty string, etc.) resolves to {@link ERole#ROLE_USER} per AAP
     *       &sect;0.5.1.6 critical rule #3 (safe default; no privilege
     *       escalation from typos). Each resolved role is added to a
     *       {@link HashSet}, deduplicating repeated requests for the same
     *       role.</li>
     *   <li><strong>Role assignment and persistence</strong> &mdash; sets
     *       the assembled role set on the {@link User} via the Lombok-
     *       generated {@code setRoles} setter, then persists the entity
     *       through {@link UserRepository#save(Object)}. Hibernate cascades
     *       the {@code @ManyToMany} association to the {@code user_roles}
     *       join table within the implicit transaction managed by Spring
     *       Data JPA's {@code SimpleJpaRepository}.</li>
     *   <li><strong>Success response</strong> &mdash; returns a
     *       {@link MessageResponse} carrying
     *       {@code "User registered successfully!"}. The controller layer
     *       wraps this in an HTTP 200 {@code ResponseEntity}.</li>
     * </ol>
     *
     * <p>The method is intentionally <em>not</em> annotated with
     * {@code @Transactional}; Spring Data JPA's {@code save} runs inside its
     * own implicit transaction, which is sufficient for this single-write
     * flow. Adding an explicit transactional boundary here would change the
     * commit semantics without changing the observed behaviour.
     *
     * <p>The method also does not perform password length or strength
     * validation: such checks are enforced upstream by Bean Validation
     * annotations on {@link SignupRequest} ({@code @NotBlank},
     * {@code @Size(min = 6, max = 40)}) and triggered by the
     * {@code @Valid} annotation on the controller's {@code @RequestBody}
     * parameter. Any violation surfaces as a
     * {@code MethodArgumentNotValidException} long before this method is
     * invoked.
     *
     * @param request the validated registration payload deserialised from
     *                the inbound JSON body; guaranteed non-{@code null} and
     *                with {@code username}, {@code email}, and
     *                {@code password} populated within their declared
     *                length bounds
     * @return a {@link MessageResponse} carrying the static success message
     *         {@code "User registered successfully!"} when the new account
     *         has been persisted
     * @throws IllegalArgumentException if the username already exists, the
     *                                  email already exists, or any
     *                                  required role row is missing from
     *                                  the {@code roles} table (which would
     *                                  indicate that the
     *                                  {@code roleSeeder} bean failed to
     *                                  populate the seed data)
     */
    public MessageResponse register(SignupRequest request) {
        // Step 1: Reject duplicate usernames before doing any expensive work.
        // Boolean.TRUE.equals(...) is null-safe: a null repository result
        // would NPE under `if (existsByUsername(...))` autoboxing.
        if (Boolean.TRUE.equals(userRepository.existsByUsername(request.getUsername()))) {
            throw new IllegalArgumentException("Error: Username is already taken!");
        }

        // Step 2: Reject duplicate emails. Order matches AAP §0.5.1.6 step 2;
        // checking username first is intentional and cheap (indexed UNIQUE
        // column) so we surface the most common collision case first.
        if (Boolean.TRUE.equals(userRepository.existsByEmail(request.getEmail()))) {
            throw new IllegalArgumentException("Error: Email is already in use!");
        }

        // Step 3: Hash the plain-text password BEFORE constructing the User
        // entity. The persisted column never carries the raw password; only
        // the BCrypt hash (60-character "$2a$10$..." form) is written.
        User user = new User(
                request.getUsername(),
                request.getEmail(),
                encoder.encode(request.getPassword()));

        // Step 4: Resolve the requested role set (or default to ROLE_USER).
        // The AAP-mandated semantics are:
        //   - null/empty role list => single ROLE_USER
        //   - "admin" (any case) => ROLE_ADMIN
        //   - anything else (including "user", "USER", unknown strings) =>
        //     fall back to ROLE_USER (safe default; prevents typo-driven
        //     privilege escalation).
        Set<String> strRoles = request.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null || strRoles.isEmpty()) {
            Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Error: Role ROLE_USER is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(roleStr -> {
                if ("admin".equalsIgnoreCase(roleStr)) {
                    Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Error: Role ROLE_ADMIN is not found."));
                    roles.add(adminRole);
                } else {
                    Role userRole = roleRepository.findByName(ERole.ROLE_USER)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Error: Role ROLE_USER is not found."));
                    roles.add(userRole);
                }
            });
        }

        // Step 5: Attach the resolved role set to the user and persist.
        // Spring Data JPA's save() runs inside its own implicit
        // REQUIRED transaction, so the User row and its user_roles
        // join-table entries are written atomically.
        user.setRoles(roles);
        userRepository.save(user);

        // Step 6: Confirm registration to the caller. The single static
        // message keeps the response payload predictable for clients.
        return new MessageResponse("User registered successfully!");
    }

    /**
     * Authenticates an existing user against the persisted credentials and
     * issues a signed JSON Web Token on success.
     *
     * <p>The method executes the following sequence:
     *
     * <ol>
     *   <li><strong>Token-based authentication</strong> &mdash; constructs an
     *       <em>unauthenticated</em>
     *       {@link UsernamePasswordAuthenticationToken} carrying the inbound
     *       username and password, then submits it to the
     *       {@link AuthenticationManager}. Internally the manager delegates
     *       to the {@code DaoAuthenticationProvider}, which loads the user
     *       via {@code UserDetailsServiceImpl.loadUserByUsername} and
     *       verifies the BCrypt hash via the shared {@link PasswordEncoder}.
     *       Failure throws {@code BadCredentialsException} (also when the
     *       username is unknown, because Spring Security's default
     *       {@code hideUserNotFoundExceptions} setting rewrites
     *       {@code UsernameNotFoundException} into the more
     *       enumeration-resistant {@code BadCredentialsException}).
     *       Per AAP &sect;0.5.1.6 critical rule #5, this exception is
     *       <strong>not caught</strong> here; it propagates unmodified to
     *       {@code GlobalExceptionHandler.handleBadCredentials} for
     *       translation to HTTP 401 with the generic message
     *       {@code "Invalid username or password"}.</li>
     *   <li><strong>SecurityContext propagation</strong> &mdash; sets the
     *       authenticated {@link Authentication} on
     *       {@link SecurityContextHolder#getContext()} so that any
     *       request-scoped collaborator that subsequently reads the
     *       security context (logging filters, audit interceptors, etc.)
     *       sees the just-authenticated principal. This is required even
     *       though the endpoint itself is stateless: per AAP &sect;0.5.1.6
     *       critical rule #6 the contract of the {@code /api/auth/login}
     *       handler includes leaving the security context populated for the
     *       remainder of the request thread.</li>
     *   <li><strong>Token issuance</strong> &mdash; calls
     *       {@link JwtUtils#generateJwtToken(Authentication)}, which extracts
     *       the username from the principal, builds a JWT with
     *       {@code sub}/{@code iat}/{@code exp} claims, and signs it with
     *       HMAC-SHA-256 using the secret loaded from {@code jwt.secret}.</li>
     *   <li><strong>Principal downcast</strong> &mdash; downcasts
     *       {@code authentication.getPrincipal()} to {@link UserDetailsImpl}
     *       to gain access to the {@code id} and {@code email} accessors
     *       that are not part of the standard {@code UserDetails} contract.
     *       Per AAP &sect;0.5.1.6 critical rule #7 the cast is safe by
     *       construction: {@code UserDetailsServiceImpl.loadUserByUsername}
     *       is the only authority on the
     *       {@code DaoAuthenticationProvider}'s configured
     *       {@code UserDetailsService}, and it always returns a
     *       {@code UserDetailsImpl}.</li>
     *   <li><strong>Authority projection</strong> &mdash; streams the
     *       principal's authorities, maps each
     *       {@link GrantedAuthority#getAuthority()} to its string form
     *       (e.g. {@code "ROLE_USER"}, {@code "ROLE_ADMIN"} &mdash; the
     *       {@code ROLE_} prefix is preserved per AAP &sect;0.7.1.1's
     *       {@link ERole}-prefix convention), and collects the result into
     *       a {@link List} suitable for the
     *       {@link JwtResponse#getRoles()} field. The
     *       {@link Collectors#toList()} terminal operator (rather than the
     *       Java&nbsp;16+ stream {@code .toList()}) is used per AAP
     *       &sect;0.5.1.6 critical rule #15 to preserve compatibility with
     *       the response field's mutable-list expectations.</li>
     *   <li><strong>Response assembly</strong> &mdash; constructs a
     *       {@link JwtResponse} via the explicit five-argument constructor
     *       {@code JwtResponse(token, id, username, email, roles)}. The
     *       {@link JwtResponse#getType() type} field defaults to the
     *       literal {@code "Bearer"} via the response DTO's field
     *       initializer; this {@code authenticate} call deliberately omits
     *       the prefix from the constructor parameter list per AAP
     *       &sect;0.5.1.6 (the single source-of-truth for the bearer prefix
     *       lives in the response DTO).</li>
     * </ol>
     *
     * <p>The method does not consult {@link UserRepository} directly:
     * {@link AuthenticationManager#authenticate(Authentication)} already
     * performs the user lookup transparently through
     * {@code UserDetailsServiceImpl}, and the resulting
     * {@link UserDetailsImpl} principal carries every field
     * ({@code id}, {@code username}, {@code email}, {@code authorities})
     * needed to populate the response. A second database hit would be
     * redundant.
     *
     * @param request the validated login payload deserialised from the
     *                inbound JSON body; guaranteed non-{@code null} and
     *                with non-blank {@code username} and {@code password}
     *                fields by the {@code @NotBlank} constraints on
     *                {@link LoginRequest}
     * @return a {@link JwtResponse} carrying the freshly-signed JWT, the
     *         literal {@code "Bearer"} prefix, the authenticated user's
     *         database id, username, email, and a list of role-name
     *         strings preserving the {@code ROLE_} prefix
     * @throws org.springframework.security.authentication.BadCredentialsException
     *         if the supplied credentials do not match a stored account, or
     *         if the supplied username does not exist (Spring Security
     *         intentionally collapses both failure modes into the same
     *         exception type to prevent user enumeration)
     */
    public JwtResponse authenticate(LoginRequest request) {
        // Step 1: Submit the unauthenticated token to Spring Security.
        // On success this returns a populated Authentication whose
        // principal is a UserDetailsImpl. On failure (wrong password OR
        // unknown username) BadCredentialsException propagates out for
        // GlobalExceptionHandler to translate into HTTP 401.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

        // Step 2: Pin the authenticated principal to the current request
        // thread's security context so downstream filters/interceptors
        // observe a populated SecurityContextHolder.
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Step 3: Mint the JWT. JwtUtils signs with HS256 using the
        // configured jwt.secret and embeds only sub/iat/exp claims (no
        // PII, no roles — roles are re-resolved from DB on every
        // protected request).
        String token = jwtUtils.generateJwtToken(authentication);

        // Step 4: Downcast to UserDetailsImpl so we can read getId() and
        // getEmail(). The cast is safe by construction: our only
        // UserDetailsService implementation always returns this concrete
        // type.
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Step 5: Project the granted authorities to a List<String> with
        // the ROLE_ prefix preserved (Spring Security's hasRole() expr
        // strips the prefix internally; the storage and wire format
        // include it). Collectors.toList() is mandated over the Java 16+
        // stream .toList() per AAP §0.5.1.6 #15 to preserve mutable-list
        // semantics for the JwtResponse.roles field.
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Step 6: Assemble the response. The 5-arg constructor leaves
        // JwtResponse.type at its "Bearer" field-initializer default.
        return new JwtResponse(
                token,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                roles);
    }
}
