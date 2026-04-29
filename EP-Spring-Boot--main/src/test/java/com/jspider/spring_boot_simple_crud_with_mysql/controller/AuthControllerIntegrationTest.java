package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.request.LoginRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.request.SignupRequest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full end-to-end integration test suite for the two public authentication
 * REST endpoints exposed by {@code AuthController}:
 * <ul>
 *   <li>{@code POST /api/auth/register} &mdash; new-user registration</li>
 *   <li>{@code POST /api/auth/login} &mdash; credential exchange for a JWT</li>
 * </ul>
 *
 * <p>This class is the canonical integration test for the JWT
 * authentication &amp; authorization feature added to
 * {@code spring-boot-simple-crud-with-mysql}. It bootstraps the FULL Spring
 * application context via {@link SpringBootTest} (NOT {@code @WebMvcTest},
 * which would mock the service layer) and drives the HTTP layer through
 * {@link MockMvc}, exercising every layer of the stack:
 *
 * <pre>{@code
 *   HTTP layer (DispatcherServlet)
 *     -> Spring Security filter chain (AuthTokenFilter, AuthorizationFilter,
 *        ExceptionTranslationFilter, AuthEntryPointJwt)
 *     -> Bean Validation (@Valid -> Hibernate Validator)
 *     -> AuthController (REST endpoints)
 *     -> AuthService (registration / authentication orchestration)
 *     -> UserRepository / RoleRepository (Spring Data JPA)
 *     -> Hibernate ORM
 *     -> H2 in-memory database
 *     -> GlobalExceptionHandler (translates exceptions to HTTP responses)
 * }</pre>
 *
 * <p><strong>Why {@link SpringBootTest} for the FULL context</strong>
 * &mdash; the test verifies the END-TO-END integration of HTTP layer through
 * to database. Mocking any layer (e.g. via {@code @MockBean}) would defeat
 * the purpose. {@code @SpringBootTest} loads every bean (the entire Spring
 * Security filter chain, all repositories, all services, the JWT components,
 * the {@code GlobalExceptionHandler}, and the role-seeder
 * {@code ApplicationRunner}), making this a true integration test.
 *
 * <p><strong>Why {@link AutoConfigureMockMvc}</strong> &mdash; this
 * annotation auto-wires a {@link MockMvc} instance backed by the
 * application context's {@code DispatcherServlet} with the Spring Security
 * filter chain applied. It removes the need for manual
 * {@code MockMvcBuilders.webAppContextSetup(...).apply(springSecurity())}
 * boilerplate and guarantees the dispatch path mirrors the production
 * filter chain exactly.
 *
 * <p><strong>Why {@link TestPropertySource}</strong> &mdash; the production
 * {@code application.properties} (per AAP &sect;0.4.1.1) targets a MySQL
 * datasource ({@code jdbc:mysql://localhost:3306/...}) that is unavailable
 * in CI/test environments. The five overrides below redirect the test
 * context to an H2 in-memory database and inject a deterministic Base64
 * {@code jwt.secret} (decoding to 64 bytes &mdash; well above the 32-byte
 * minimum mandated by RFC 7518 &sect;3.2 for HS256) plus a 1-hour
 * {@code jwt.expiration}. Property declaration order matters: the datasource
 * properties must be set before the JPA layer initialises so Hibernate boots
 * against H2 rather than the production MySQL URL.
 *
 * <p><strong>Test isolation strategy</strong> &mdash; each of the six test
 * methods uses a UNIQUE username ({@code alice}, {@code bob_dup},
 * {@code carol}, {@code dave}, {@code eve}, {@code nonexistent_user}) so
 * that writes from one test cannot collide with another inside the shared
 * H2 instance. This is preferred over {@code @Transactional} rollback
 * because mixing transactional rollback with Spring Security's
 * {@code AuthenticationManager} and {@code BCryptPasswordEncoder} can yield
 * subtle bugs (e.g. lazy-initialisation issues on {@code User.roles}).
 *
 * <p><strong>Class-level visibility</strong> &mdash; this class is
 * intentionally <em>package-private</em> (no {@code public} modifier) to
 * mirror the existing {@code SpringBootSimpleCrudWithMysqlApplicationTests}
 * convention. JUnit 5 discovers package-private test classes via the
 * JUnit Platform's classpath scanning regardless of the {@code public}
 * modifier.
 *
 * <p><strong>Independence from other tests</strong> &mdash; each method
 * sets up its own preconditions inline (e.g. registering before logging
 * in). No {@code @BeforeEach}, {@code @AfterEach}, or shared helper
 * methods are used; this satisfies the AAP's mandate that the suite
 * comprise EXACTLY six {@code @Test} methods with no auxiliary scaffolding.
 *
 * @see SpringBootTest
 * @see AutoConfigureMockMvc
 * @see TestPropertySource
 * @see com.jspider.spring_boot_simple_crud_with_mysql.controller
 *      .AuthController
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "jwt.secret=dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItdW5pdC10ZXN0aW5nLW9ubHktbm90LWZvci1wcm9kdWN0aW9uLXVzZQ==",
    "jwt.expiration=3600000"
})
class AuthControllerIntegrationTest {

    /**
     * MockMvc bean auto-configured by {@link AutoConfigureMockMvc} with the
     * Spring Security filter chain applied. All HTTP request simulations in
     * this test class are dispatched through this instance.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Jackson {@link ObjectMapper} auto-configured by Spring Boot's
     * {@code JacksonAutoConfiguration}. Used to serialise the in-memory
     * {@link SignupRequest} and {@link LoginRequest} DTOs into JSON strings
     * for use as MockMvc request bodies.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Verifies that {@code POST /api/auth/register} returns HTTP 200 OK and
     * a {@code MessageResponse} body for a valid registration request.
     *
     * <p>Flow under test:</p>
     * <ol>
     *   <li>The MockMvc client posts a JSON body with username
     *       {@code "alice"}, email {@code "alice@example.com"}, password
     *       {@code "secret123"}, and role set {@code Set.of("user")} to
     *       {@code /api/auth/register}.</li>
     *   <li>The Spring Security filter chain admits the request because
     *       {@code SecurityConfig} declares {@code permitAll()} for
     *       {@code /api/auth/**}.</li>
     *   <li>Bean Validation runs ({@code @Valid} on the controller method
     *       parameter) and passes because all fields meet their constraints
     *       (username 5 chars within 3-20, email valid form, password 9
     *       chars within 6-40).</li>
     *   <li>{@code AuthService.register(SignupRequest)} hashes the password
     *       via {@code BCryptPasswordEncoder.encode}, resolves the role
     *       string {@code "user"} to {@code ERole.ROLE_USER}, persists a
     *       new {@code User} entity, and returns
     *       {@code new MessageResponse("User registered successfully!")}.</li>
     *   <li>{@code AuthController.registerUser} wraps the response in
     *       {@code ResponseEntity.ok(...)}.</li>
     * </ol>
     *
     * <p>Assertions:</p>
     * <ul>
     *   <li>HTTP status is 200 (via {@code status().isOk()})</li>
     *   <li>JSON {@code $.message} contains the substring
     *       {@code "User registered"} &mdash; using
     *       {@link org.hamcrest.Matchers#containsString containsString}
     *       to tolerate minor variations in the success message (e.g.
     *       trailing punctuation).</li>
     * </ul>
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    void register_validRequest_returns200() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("secret123");
        request.setRole(Set.of("user"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("User registered")));
    }

    /**
     * Verifies that a second registration attempt with an already-taken
     * username returns HTTP 400 Bad Request with a meaningful error message.
     *
     * <p>This test performs TWO sequential registrations within the same
     * method body:</p>
     * <ol>
     *   <li>The first registration with username {@code "bob_dup"}, email
     *       {@code "bob1@example.com"}, password {@code "secret123"} MUST
     *       succeed (status 200), establishing the persisted record that
     *       creates the duplicate condition.</li>
     *   <li>The second registration with username {@code "bob_dup"} (same),
     *       email {@code "bob2@example.com"} (different), password
     *       {@code "secret123"} MUST fail with status 400. The
     *       {@code AuthService.register} method calls
     *       {@code userRepository.existsByUsername("bob_dup")}, which
     *       returns {@code true} (because of step 1), and then throws
     *       {@code IllegalArgumentException("Error: Username is already
     *       taken!")}. The {@code GlobalExceptionHandler.handleIllegalArgument}
     *       method translates this to HTTP 400 with {@code ex.getMessage()}
     *       in the response body.</li>
     * </ol>
     *
     * <p>The {@code _dup} suffix on the username deliberately avoids
     * collision with usernames used by other tests in this class
     * ({@code alice}, {@code carol}, {@code dave}, {@code eve}). Different
     * email addresses on the two requests ensure the rejection is driven
     * by the username uniqueness constraint, not the email constraint
     * (which is checked next in {@code AuthService.register}).
     *
     * <p>The substring assertion {@code containsString("Username is already
     * taken")} on the second response body tolerates the leading
     * {@code "Error: "} prefix and trailing {@code "!"} punctuation in the
     * full message {@code "Error: Username is already taken!"}.
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    void register_duplicateUsername_returns400() throws Exception {
        // First registration succeeds and seeds the duplicate condition.
        SignupRequest first = new SignupRequest();
        first.setUsername("bob_dup");
        first.setEmail("bob1@example.com");
        first.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        // Second registration with same username (different email) must fail with 400.
        SignupRequest duplicate = new SignupRequest();
        duplicate.setUsername("bob_dup");
        duplicate.setEmail("bob2@example.com");
        duplicate.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Username is already taken")));
    }

    /**
     * Verifies that an invalid email address triggers a Bean Validation
     * failure and returns HTTP 400 Bad Request.
     *
     * <p>The {@code SignupRequest.email} field is annotated with
     * {@code @Email}, which Hibernate Validator checks against an RFC 5322-
     * style regex. The literal value {@code "not-an-email"} fails the regex
     * (no {@code @} character), causing Spring's request-binding layer to
     * throw {@code MethodArgumentNotValidException}. The
     * {@code GlobalExceptionHandler.handleValidationExceptions} method
     * translates this to HTTP 400 with a field-error map.
     *
     * <p>End-to-end Bean Validation flow under test:
     * <pre>{@code
     *   HTTP layer (POST body deserialisation by Jackson)
     *     -> @Valid annotation on AuthController#registerUser parameter
     *     -> Hibernate Validator runs all constraints on SignupRequest
     *     -> @Email constraint on `email` field fails for "not-an-email"
     *     -> MethodArgumentNotValidException thrown by Spring
     *     -> @RestControllerAdvice (GlobalExceptionHandler)
     *     -> HTTP 400 response with field-error map body
     * }</pre>
     *
     * <p>This test deliberately does NOT assert on the response body
     * because the {@code GlobalExceptionHandler.handleValidationExceptions}
     * returns a {@code Map<String, String>} of field-name -&gt; error-message
     * whose exact format depends on Hibernate Validator's default messages
     * (which can vary across patch versions). Asserting only on status 400
     * keeps the test resilient to such minor format changes while still
     * verifying that Bean Validation fired end-to-end.
     *
     * <p>Username {@code "carol"} is unique among this test class's
     * usernames, preserving the per-test isolation strategy even though
     * this registration FAILS (so {@code carol} is never persisted).
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    void register_invalidEmail_returns400() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("carol");
        request.setEmail("not-an-email");
        request.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies the happy-path login flow: register a new user, then log in
     * with the correct credentials, and assert that the response is HTTP
     * 200 with a fully-populated {@code JwtResponse} body.
     *
     * <p>This test performs TWO sequential operations:</p>
     * <ol>
     *   <li><strong>Registration</strong> &mdash; creates user
     *       {@code "dave"} with email {@code "dave@example.com"} and
     *       password {@code "secret123"}. No {@code role} is supplied,
     *       exercising the default-{@code ROLE_USER} branch in
     *       {@code AuthService.register}. The registration response is
     *       asserted as 200 OK to confirm the user is persisted before
     *       the login attempt.</li>
     *   <li><strong>Login</strong> &mdash; submits a {@link LoginRequest}
     *       with the same username {@code "dave"} and password
     *       {@code "secret123"}.
     *       {@code AuthService.authenticate(LoginRequest)} delegates to
     *       Spring Security's {@code AuthenticationManager}, which loads
     *       the persisted user via {@code UserDetailsServiceImpl} and
     *       verifies the password against the BCrypt hash via
     *       {@code BCryptPasswordEncoder.matches}. On success it generates
     *       a JWT via {@code JwtUtils.generateJwtToken(Authentication)}
     *       and returns a {@code JwtResponse} populated with the token,
     *       type {@code "Bearer"}, the persisted user's id, username,
     *       email, and roles list ({@code ["ROLE_USER"]}).</li>
     * </ol>
     *
     * <p>The JSON response is asserted via six {@code jsonPath} matchers
     * against the {@code JwtResponse} contract:</p>
     * <ul>
     *   <li>{@code $.token} &mdash; non-null (a long signed JWT string)</li>
     *   <li>{@code $.type} &mdash; equals literal {@code "Bearer"}</li>
     *   <li>{@code $.id} &mdash; non-null (the auto-generated user id)</li>
     *   <li>{@code $.username} &mdash; equals {@code "dave"}</li>
     *   <li>{@code $.email} &mdash; equals {@code "dave@example.com"}
     *       (loaded from the persisted entity, NOT from the login
     *       request)</li>
     *   <li>{@code $.roles} &mdash; non-null (a JSON array containing
     *       {@code "ROLE_USER"})</li>
     * </ul>
     *
     * <p>The {@code .value(...)} form is preferred over Hamcrest
     * {@code is(...)} for fixed-value assertions because it avoids the
     * extra static import and keeps the test imports minimal.
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        // Register the user so the login step has a valid principal to authenticate.
        SignupRequest signup = new SignupRequest();
        signup.setUsername("dave");
        signup.setEmail("dave@example.com");
        signup.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        // Login with the matching credentials.
        LoginRequest login = new LoginRequest();
        login.setUsername("dave");
        login.setPassword("secret123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username").value("dave"))
                .andExpect(jsonPath("$.email").value("dave@example.com"))
                .andExpect(jsonPath("$.roles", notNullValue()));
    }

    /**
     * Verifies that submitting an incorrect password for an existing user
     * returns HTTP 401 Unauthorized.
     *
     * <p>The test registers user {@code "eve"} with the password
     * {@code "correctpass"} (asserts 200), then attempts to log in with the
     * username {@code "eve"} and password {@code "wrongpassword"}. Spring
     * Security's {@code AuthenticationManager.authenticate} calls
     * {@code BCryptPasswordEncoder.matches("wrongpassword", &lt;hash of
     * "correctpass"&gt;)} which returns {@code false}, causing
     * {@code AuthenticationManager} to throw
     * {@code BadCredentialsException}. The
     * {@code GlobalExceptionHandler.handleBadCredentials} method translates
     * this to HTTP 401 with {@code MessageResponse("Invalid username or
     * password")}.
     *
     * <p>This test deliberately does NOT assert on the response body's
     * {@code message} field. Asserting only on status 401 is sufficient
     * to verify the security behaviour and keeps the test robust to minor
     * message-format changes in the {@code GlobalExceptionHandler}.
     *
     * <p>Username {@code "eve"} is unique among this test class's
     * usernames, satisfying the per-test isolation requirement.
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    void login_invalidPassword_returns401() throws Exception {
        // Register the user with a known correct password.
        SignupRequest signup = new SignupRequest();
        signup.setUsername("eve");
        signup.setEmail("eve@example.com");
        signup.setPassword("correctpass");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        // Attempt login with the WRONG password.
        LoginRequest login = new LoginRequest();
        login.setUsername("eve");
        login.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies that login attempts for a NEVER-registered username return
     * HTTP 401 Unauthorized (NOT HTTP 404).
     *
     * <p>This test deliberately does NOT register a user. It builds a
     * {@link LoginRequest} with username {@code "nonexistent_user"} and
     * password {@code "anything"}, and posts it to
     * {@code /api/auth/login}.
     *
     * <p>Flow under test:</p>
     * <ol>
     *   <li>Spring Security's {@code DaoAuthenticationProvider} calls
     *       {@code UserDetailsServiceImpl.loadUserByUsername("nonexistent_user")},
     *       which returns {@code Optional.empty()} from
     *       {@code UserRepository.findByUsername} and throws
     *       {@code UsernameNotFoundException}.</li>
     *   <li>Because {@code DaoAuthenticationProvider}'s default
     *       {@code hideUserNotFoundExceptions} is {@code true}, it converts
     *       the {@code UsernameNotFoundException} into a generic
     *       {@code BadCredentialsException}. This prevents user-enumeration
     *       attacks: an attacker probing the login endpoint cannot
     *       distinguish between "user does not exist" and "wrong
     *       password" responses.</li>
     *   <li>{@code GlobalExceptionHandler.handleBadCredentials} translates
     *       the {@code BadCredentialsException} to HTTP 401.</li>
     * </ol>
     *
     * <p>Asserting only on status 401 is sufficient: the AAP requires that
     * the response is identical to the {@code login_invalidPassword_returns401}
     * test, which is exactly what {@code hideUserNotFoundExceptions=true}
     * guarantees.
     *
     * <p>The username {@code "nonexistent_user"} is intentionally distinct
     * from every other username in this test class so it CANNOT collide
     * with a previously-registered record (regardless of test execution
     * order).
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    void login_unknownUser_returns401() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setUsername("nonexistent_user");
        login.setPassword("anything");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }
}
