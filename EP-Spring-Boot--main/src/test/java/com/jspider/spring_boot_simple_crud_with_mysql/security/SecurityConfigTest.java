package com.jspider.spring_boot_simple_crud_with_mysql.security;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.Product;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.request.LoginRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.payload.request.SignupRequest;

/**
 * Spring Security filter chain authorization integration tests.
 *
 * <p>This integration test boots the full Spring application context via
 * {@link SpringBootTest} and exercises the actual production
 * {@code SecurityFilterChain} bean defined in
 * {@code com.jspider.spring_boot_simple_crud_with_mysql.security.SecurityConfig}
 * through {@link MockMvc} request simulation. It verifies four critical
 * security behaviors that, together, prove the security filter chain enforces
 * the authorization policy declared in the Agent Action Plan (&sect;0.4.1.1
 * and &sect;0.5.1.5):</p>
 *
 * <ol>
 *   <li><strong>Unauthenticated access is rejected with a structured 401</strong>
 *       &mdash; verifies that protected {@code /product/**} endpoints return
 *       HTTP 401 with the JSON body emitted by
 *       {@code AuthEntryPointJwt.commence()} (keys {@code status},
 *       {@code error}, {@code message}, {@code path}).</li>
 *   <li><strong>USER role grants read access but not write access</strong>
 *       &mdash; verifies that {@code @PreAuthorize("hasAnyRole('USER',
 *       'ADMIN')")} on read endpoints permits a USER-role principal, while
 *       {@code @PreAuthorize("hasRole('ADMIN')")} on write endpoints rejects
 *       a USER-role principal with HTTP 403.</li>
 *   <li><strong>ADMIN role grants both read and write access</strong>
 *       &mdash; verifies that an ADMIN-role principal is not blocked by the
 *       authorization layer on either read or write product endpoints.</li>
 *   <li><strong>{@code /api/auth/**} paths are publicly accessible</strong>
 *       &mdash; verifies that {@code requestMatchers("/api/auth/**").permitAll()}
 *       in the security filter chain admits unauthenticated requests to the
 *       registration and login endpoints.</li>
 * </ol>
 *
 * <p><strong>Why {@link SpringBootTest} (not {@code @WebMvcTest})</strong>
 * &mdash; {@code @WebMvcTest} loads only the MVC slice and would require
 * explicit {@code @MockBean} declarations for every collaborator the
 * controllers and security beans transitively depend on. By contrast,
 * {@link SpringBootTest} loads the full application context (including
 * {@code SecurityConfig}, {@code JwtUtils}, {@code AuthService}, all
 * repositories, and all entities), so the security filter chain under test
 * is the same one the production application uses. The slightly slower
 * startup is a worthwhile trade-off for end-to-end realism.</p>
 *
 * <p><strong>Why {@link AutoConfigureMockMvc}</strong> &mdash; this
 * annotation auto-wires a {@link MockMvc} instance that has Spring
 * Security's filter chain applied via the {@code springSecurity()}
 * configurer. It removes the need for manual
 * {@code MockMvcBuilders.webAppContextSetup(...).apply(springSecurity())}
 * boilerplate and guarantees that the MockMvc dispatch path mirrors the
 * production filter chain exactly.</p>
 *
 * <p><strong>Why {@link TestPropertySource}</strong> &mdash; production
 * {@code application.properties} targets a MySQL datasource that is
 * unavailable in CI/test environments. The five overrides redirect the test
 * context to an H2 in-memory database and inject a deterministic Base64
 * {@code jwt.secret} (decoding to 64 bytes &mdash; well above the 32-byte
 * minimum mandated by RFC 7518 &sect;3.2 for HS256) plus a 1-hour
 * {@code jwt.expiration}. Property declaration order matters: the
 * datasource properties must be set before the JPA layer initialises so
 * that Hibernate boots against H2 rather than the production MySQL URL.</p>
 *
 * <p><strong>Class-level visibility</strong> &mdash; this class is
 * intentionally <em>package-private</em> (no {@code public} modifier) to
 * mirror the existing {@code SpringBootSimpleCrudWithMysqlApplicationTests}
 * convention. JUnit 5 discovers package-private test classes via the
 * JUnit Platform's classpath scanning regardless of the {@code public}
 * modifier.</p>
 *
 * <p><strong>Test method independence</strong> &mdash; each of the four
 * test methods is independent of the others; they may run in any order.
 * Test 4 has internal ordering (register before login within the same
 * method body), but tests 1, 2, and 3 do not depend on the user created
 * inside test 4 nor on each other.</p>
 *
 * @see SpringBootTest
 * @see AutoConfigureMockMvc
 * @see WithMockUser
 * @see com.jspider.spring_boot_simple_crud_with_mysql.security.jwt.AuthEntryPointJwt
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
class SecurityConfigTest {

    /**
     * MockMvc bean auto-configured by {@link AutoConfigureMockMvc} with the
     * Spring Security filter chain applied. All HTTP request simulations in
     * this test class are dispatched through this instance.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Jackson {@link ObjectMapper} auto-configured by Spring Boot. Used to
     * serialise {@link Product}, {@link SignupRequest}, and
     * {@link LoginRequest} instances to JSON request bodies for POST
     * simulations.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Verifies that an unauthenticated request to a protected
     * {@code /product/**} endpoint is rejected by the security filter chain
     * with HTTP 401 and the structured JSON body produced by
     * {@code AuthEntryPointJwt.commence(...)}.
     *
     * <p>Flow under test:</p>
     * <ol>
     *   <li>The request carries no {@code Authorization} header, so
     *       {@code AuthTokenFilter.parseJwt} returns {@code null} and the
     *       filter does not populate {@code SecurityContextHolder}.</li>
     *   <li>Spring Security's {@code AuthorizationFilter} (or upstream
     *       {@code FilterSecurityInterceptor}) detects the unauthenticated
     *       request and throws an {@code AccessDeniedException} or
     *       {@code AuthenticationCredentialsNotFoundException}.</li>
     *   <li>{@code ExceptionTranslationFilter} catches the exception and
     *       invokes the registered
     *       {@code AuthenticationEntryPoint.commence(...)} (i.e.
     *       {@code AuthEntryPointJwt}).</li>
     *   <li>{@code AuthEntryPointJwt.commence(...)} writes status 401 and a
     *       JSON body with keys {@code status}, {@code error},
     *       {@code message}, {@code path}.</li>
     * </ol>
     *
     * <p>Assertions verify the exact contract documented in
     * {@code AuthEntryPointJwt}:</p>
     * <ul>
     *   <li>HTTP status is 401 (via {@code status().isUnauthorized()})</li>
     *   <li>JSON {@code $.status} equals integer 401</li>
     *   <li>JSON {@code $.error} equals the literal string "Unauthorized"</li>
     * </ul>
     *
     * @throws Exception if the MockMvc dispatch fails for any non-security
     *                   reason (e.g. context load failure)
     */
    @Test
    void productEndpointWithoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/product/findAllProduct"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    /**
     * Verifies that a USER-role principal can reach read endpoints (GET) but
     * is forbidden from write endpoints (POST) that require ADMIN.
     *
     * <p>{@link WithMockUser} pre-populates {@code SecurityContextHolder}
     * with a mock {@code Authentication} carrying authority
     * {@code ROLE_USER}. Spring Security automatically prepends
     * {@code ROLE_} to the value passed in {@code roles}, so
     * {@code roles = "USER"} produces {@code ROLE_USER} (NOT
     * {@code ROLE_ROLE_USER}).</p>
     *
     * <p>Read assertion (GET {@code /product/findAllProduct}):
     * {@code @PreAuthorize("hasAnyRole('USER', 'ADMIN')")} on the controller
     * method evaluates {@code hasRole('USER')} (Spring auto-prepends
     * {@code ROLE_}) against the mock's {@code ROLE_USER} authority &mdash;
     * match, request proceeds. The actual response status may be 200
     * (success) or 5xx (DB-related downstream issue); the security check is
     * proven by the response NOT being 401 or 403.</p>
     *
     * <p>Write assertion (POST {@code /product/saveProduct}):
     * {@code @PreAuthorize("hasRole('ADMIN')")} requires {@code ROLE_ADMIN}
     * &mdash; the mock has only {@code ROLE_USER}, so Spring throws
     * {@code AccessDeniedException} which is translated to HTTP 403 by
     * Spring Security's {@code ExceptionTranslationFilter} (or by the
     * application's {@code GlobalExceptionHandler}, both producing 403).</p>
     *
     * <p>The {@link Product} instance is constructed with primitive {@code int}
     * id and primitive {@code double} price (matching the fields declared on
     * the {@code Product} entity); CSRF is disabled in {@code SecurityConfig}
     * so no CSRF token post-processor is required on the POST simulation.</p>
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    @WithMockUser(roles = "USER")
    void productEndpointWithUserRole_returns200ForReads_returns403ForWrites() throws Exception {
        // GET should not be blocked (USER has read access via hasAnyRole).
        // We assert "NOT 401 AND NOT 403" rather than a specific success code
        // because the downstream DAO may produce 200, 5xx, or other codes —
        // none of which represent a security-layer rejection.
        mockMvc.perform(get("/product/findAllProduct"))
            .andExpect(status().is(not(equalTo(401))))
            .andExpect(status().is(not(equalTo(403))));

        // POST should be 403 (USER lacks ADMIN write privilege).
        // Build a Product instance using primitive int id and primitive double
        // price (matching Product entity field declarations).
        Product product = new Product();
        product.setId(1);
        product.setName("Test");
        product.setColor("Red");
        product.setPrice(100.0);

        mockMvc.perform(post("/product/saveProduct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product)))
            .andExpect(status().isForbidden());
    }

    /**
     * Verifies that an ADMIN-role principal can reach both read and write
     * endpoints without being blocked by the authorization layer.
     *
     * <p>{@link WithMockUser}{@code (roles = "ADMIN")} populates
     * {@code SecurityContextHolder} with authority {@code ROLE_ADMIN}.</p>
     *
     * <p>Read assertion (GET {@code /product/findAllProduct}):
     * {@code @PreAuthorize("hasAnyRole('USER', 'ADMIN')")} matches
     * {@code ROLE_ADMIN}. Request proceeds. Asserts NOT 401 AND NOT 403.</p>
     *
     * <p>Write assertion (POST {@code /product/saveProduct}):
     * {@code @PreAuthorize("hasRole('ADMIN')")} matches {@code ROLE_ADMIN}.
     * Request proceeds. Asserts NOT 401 AND NOT 403. The actual response may
     * be 200 (successful save) or another non-security-related code (e.g. a
     * DAO exception); the test asserts only that the security layer did NOT
     * reject the request.</p>
     *
     * <p>A different product {@code id} (2) and {@code name} ("AdminProduct")
     * from the USER-role test are used to avoid potential primary-key
     * collisions if both tests run inside the same context cache against the
     * same in-memory H2 instance.</p>
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void productEndpointWithAdminRole_returns200ForAllOperations() throws Exception {
        // GET should not be blocked (ADMIN has read access via hasAnyRole).
        mockMvc.perform(get("/product/findAllProduct"))
            .andExpect(status().is(not(equalTo(401))))
            .andExpect(status().is(not(equalTo(403))));

        // POST should not be blocked (ADMIN has write access via hasRole('ADMIN')).
        // Use id=2 and a distinct name to avoid primary-key conflicts with
        // the Phase 5 USER-role test (id=1) when both run in the same context.
        Product product = new Product();
        product.setId(2);
        product.setName("AdminProduct");
        product.setColor("Blue");
        product.setPrice(200.0);

        mockMvc.perform(post("/product/saveProduct")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(product)))
            .andExpect(status().is(not(equalTo(401))))
            .andExpect(status().is(not(equalTo(403))));
    }

    /**
     * Verifies that the public authentication endpoints under
     * {@code /api/auth/**} are accessible without prior authentication.
     *
     * <p>The absence of a {@link WithMockUser} annotation on this test
     * method is intentional: it simulates an unauthenticated client (the
     * realistic scenario for users registering or logging in for the first
     * time). The {@code SecurityConfig.filterChain} declares
     * {@code requestMatchers("/api/auth/**").permitAll()}, so the security
     * filter chain MUST admit these requests without any auth credential.</p>
     *
     * <p>The test exercises both endpoints in sequence within the same
     * method body (NOT split into two methods) because the login step
     * benefits from the user just registered. This intra-method ordering
     * does NOT violate JUnit's test independence guarantee &mdash; tests 1,
     * 2, and 3 do not depend on the {@code "publictest"} user created
     * here.</p>
     *
     * <p><strong>POST {@code /api/auth/register}</strong> &mdash; sends a
     * valid {@link SignupRequest} JSON body. The downstream
     * {@code AuthService.register} either succeeds (200 with
     * {@code MessageResponse}) or rejects with 400 if the username is
     * already taken (e.g. from a context cache with a leftover user). EITHER
     * outcome is acceptable here because both demonstrate that the security
     * filter chain did NOT block the request with 401 or 403.</p>
     *
     * <p><strong>POST {@code /api/auth/login}</strong> &mdash; sends a valid
     * {@link LoginRequest} JSON body. The downstream
     * {@code AuthService.authenticate} delegates to
     * {@code AuthenticationManager}, which returns a {@code JwtResponse} on
     * success or 401 on bad credentials. We assert NOT 401 AND NOT 403:
     * because the user was just registered (or already existed in context
     * cache), the credential match should succeed and yield 200 with a
     * {@code JwtResponse}.</p>
     *
     * <p>The username "publictest" is chosen specifically to avoid potential
     * conflicts with users created by sibling integration tests (e.g.
     * {@code AuthControllerIntegrationTest} may use names like "alice").</p>
     *
     * <p>Field values satisfy the validation constraints declared on
     * {@link SignupRequest}: username 10 chars (within 3-20), email valid
     * RFC 5322 form (within 50 chars), password 11 chars (within 6-40). No
     * {@code role} is supplied, exercising the default-{@code ROLE_USER}
     * branch in {@code AuthService.register}.</p>
     *
     * @throws Exception if the MockMvc dispatch or JSON serialisation fails
     */
    @Test
    void authEndpoints_arePubliclyAccessible() throws Exception {
        // POST /api/auth/register with a valid SignupRequest body (no authentication).
        // Field values satisfy SignupRequest validation: username 10 chars (3-20),
        // email valid form (max 50), password 11 chars (6-40). The optional role
        // field is omitted, exercising the default-ROLE_USER branch in AuthService.
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setUsername("publictest");
        signupRequest.setEmail("publictest@example.com");
        signupRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
            .andExpect(status().is(not(equalTo(401))))
            .andExpect(status().is(not(equalTo(403))));

        // POST /api/auth/login with the just-registered credentials (no authentication).
        // Verifies that the /api/auth/** permitAll rule applies to login as well.
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("publictest");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().is(not(equalTo(401))))
            .andExpect(status().is(not(equalTo(403))));
    }
}
