package com.jspider.spring_boot_simple_crud_with_mysql.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.jspider.spring_boot_simple_crud_with_mysql.security.jwt.AuthEntryPointJwt;
import com.jspider.spring_boot_simple_crud_with_mysql.security.jwt.AuthTokenFilter;
import com.jspider.spring_boot_simple_crud_with_mysql.security.services.UserDetailsServiceImpl;

/**
 * Central Spring Security configuration for the JWT-based authentication and
 * authorization layer of the {@code spring-boot-simple-crud-with-mysql}
 * application.
 *
 * <p>This {@link Configuration @Configuration} class composes the complete
 * security infrastructure for the project: it instantiates the JWT filter
 * ({@link AuthTokenFilter}), wires the unauthorized-access entry point
 * ({@link AuthEntryPointJwt}), configures the
 * {@link DaoAuthenticationProvider} that bridges the JPA-backed
 * {@link UserDetailsServiceImpl} to Spring Security's authentication
 * pipeline, exposes the framework's {@link AuthenticationManager} for
 * programmatic invocation by {@code AuthService}, and assembles a stateless
 * {@link SecurityFilterChain} that enforces role-based authorization on
 * every endpoint of the application.</p>
 *
 * <h2>Filter chain shape</h2>
 *
 * <p>The {@link #filterChain(HttpSecurity) filterChain(HttpSecurity)} bean
 * declares the following invariants on every request:</p>
 *
 * <ul>
 *   <li>CSRF protection is intentionally disabled because the application is
 *       a stateless REST API authenticated via the {@code Authorization}
 *       header (not via cookies). With JWT in the header, browsers do not
 *       auto-attach the token on cross-origin requests, so CSRF attacks are
 *       impossible. See the in-line code comment on the {@code csrf}
 *       configuration for the full rationale.</li>
 *   <li>The {@link AuthEntryPointJwt} is registered as the
 *       {@code AuthenticationEntryPoint} so that anonymous requests reaching
 *       a protected endpoint receive a structured HTTP 401 JSON response
 *       (see {@link AuthEntryPointJwt#commence}).</li>
 *   <li>Session creation is set to
 *       {@link SessionCreationPolicy#STATELESS}, guaranteeing that no
 *       {@code HttpSession} is created and no {@code JSESSIONID} cookie is
 *       issued. Each request must independently re-authenticate via its own
 *       JWT, enabling horizontal scaling without session affinity.</li>
 *   <li>Authorization rules are evaluated in declaration order:
 *       {@code /api/auth/**} and the OpenAPI/Swagger paths are
 *       {@code permitAll()}; everything else falls through to
 *       {@code anyRequest().authenticated()}, which the
 *       {@link AuthTokenFilter}-populated {@code SecurityContext} either
 *       satisfies (valid JWT present) or fails (no/invalid JWT, triggering
 *       the {@link AuthEntryPointJwt}).</li>
 *   <li>The {@link AuthTokenFilter} is registered <em>before</em>
 *       {@link UsernamePasswordAuthenticationFilter} via
 *       {@link HttpSecurity#addFilterBefore(jakarta.servlet.Filter, Class)},
 *       which is the canonical insertion point for token-based
 *       authentication: by the time the request reaches the framework's
 *       authorization filter, the JWT has already been validated and the
 *       authenticated principal placed on the {@code SecurityContextHolder}.</li>
 * </ul>
 *
 * <h2>Method-level security</h2>
 *
 * <p>The class is annotated with
 * {@link EnableMethodSecurity @EnableMethodSecurity(prePostEnabled = true)},
 * which activates Spring Security 6.x's
 * {@link org.springframework.security.access.prepost.PreAuthorize @PreAuthorize}
 * and {@code @PostAuthorize} interceptors. This allows
 * {@link com.jspider.spring_boot_simple_crud_with_mysql.controller.ProductController}
 * and
 * {@link com.jspider.spring_boot_simple_crud_with_mysql.controller.StudentController}
 * to declare per-method role requirements such as
 * {@code @PreAuthorize("hasRole('ADMIN')")} and
 * {@code @PreAuthorize("hasAnyRole('USER', 'ADMIN')")}. The deprecated
 * {@code @EnableGlobalMethodSecurity} annotation is intentionally NOT used
 * because it has been removed from the Spring Security roadmap.</p>
 *
 * <h2>Why {@code AuthTokenFilter} is registered as a {@code @Bean} (not via
 * stereotype scan)</h2>
 *
 * <p>The {@link AuthTokenFilter} class is deliberately not annotated with
 * {@code @Component}. Were it stereotype-annotated, Spring Boot's default
 * servlet-filter auto-registration would wire it into the global filter
 * chain via a synthesized {@code FilterRegistrationBean} <em>in addition</em>
 * to the security filter chain established by {@link #filterChain}. The
 * filter would then run twice per request, doubling JWT-validation cost and
 * triggering two database lookups against
 * {@link UserDetailsServiceImpl#loadUserByUsername(String)}. Registering it
 * exclusively through the {@link #authenticationJwtTokenFilter()} bean
 * factory keeps the filter under the security chain's sole control.</p>
 *
 * <h2>Why field injection on a {@code @Configuration} class</h2>
 *
 * <p>Field injection of {@link UserDetailsServiceImpl} and
 * {@link AuthEntryPointJwt} via {@link Autowired @Autowired} is the canonical
 * Spring Security 6.x pattern for security configuration classes. While
 * constructor injection is the broader recommendation for
 * {@code @Service}/{@code @Component} beans (and is used throughout
 * {@code AuthService} and the controller layer), {@code @Configuration}
 * classes are exempt because they are processed by Spring's CGLIB-based
 * bean post-processor early in the application context lifecycle. The
 * field-injection idiom keeps the class minimal and is documented in the
 * project's Agent Action Plan §0.7.1.1.</p>
 *
 * <h2>Out-of-scope concerns</h2>
 *
 * <ul>
 *   <li>CORS configuration lives in {@code WebSecurityCorsConfig} (a sibling
 *       {@code @Configuration} class) and is not declared here. The two
 *       configurations are orthogonal and Spring Security composes them
 *       automatically when both beans are present in the context.</li>
 *   <li>Refresh-token logic, HTTPS configuration, rate limiting, and
 *       server-side logout are explicitly out of scope per AAP §0.6.2.</li>
 * </ul>
 *
 * @see AuthTokenFilter
 * @see AuthEntryPointJwt
 * @see UserDetailsServiceImpl
 * @see SecurityFilterChain
 * @see HttpSecurity
 * @see SessionCreationPolicy
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Spring-managed {@link UserDetailsServiceImpl} that adapts the JPA
     * {@code User} entity into Spring Security's
     * {@link org.springframework.security.core.userdetails.UserDetails}
     * contract. Injected into {@link #authenticationProvider() the
     * authentication provider} via the
     * {@link DaoAuthenticationProvider#DaoAuthenticationProvider(org.springframework.security.core.userdetails.UserDetailsService)
     * single-argument constructor} to enable credential resolution against
     * the database during the {@code POST /api/auth/login} flow.
     *
     * <p>The field is typed against the concrete
     * {@link UserDetailsServiceImpl} class (rather than the
     * {@link org.springframework.security.core.userdetails.UserDetailsService
     * UserDetailsService} interface) to avoid bean-disambiguation friction
     * if a future feature introduces an alternate implementation, and to
     * mirror the type used by {@link AuthTokenFilter}.</p>
     */
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    /**
     * Spring-managed {@link AuthEntryPointJwt} bean that converts
     * authentication failures into a structured HTTP 401 JSON response.
     * Registered on the {@link HttpSecurity} builder via
     * {@code http.exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))}
     * inside {@link #filterChain(HttpSecurity)}.
     *
     * <p>Spring Security's {@code ExceptionTranslationFilter} delegates to
     * this entry point whenever an anonymous request hits a path requiring
     * authentication; the entry point writes the canonical JSON envelope
     * {@code {"status": 401, "error": "Unauthorized", "message": "...",
     * "path": "..."}} that REST clients can parse programmatically.</p>
     */
    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    /**
     * Exposes the JWT extraction/validation filter as a Spring-managed
     * singleton so that the framework's bean post-processor can populate
     * its {@code @Autowired} fields ({@code jwtUtils} and
     * {@code userDetailsService}) before the filter is registered on the
     * security chain.
     *
     * <p>This is the single, intentional registration point for
     * {@link AuthTokenFilter}. The class itself is deliberately not
     * stereotype-annotated, which keeps Spring Boot's default servlet-filter
     * auto-registration from wiring the filter into the global servlet
     * filter chain via a synthesized {@code FilterRegistrationBean} in
     * addition to the security chain established by {@link #filterChain}.</p>
     *
     * @return a fresh {@link AuthTokenFilter} instance whose dependencies
     *         will be auto-wired by Spring after this method returns
     */
    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    /**
     * Configures the {@link DaoAuthenticationProvider} that Spring
     * Security's {@link AuthenticationManager} consults at login time to
     * verify the username/password credentials carried in a
     * {@code LoginRequest} payload.
     *
     * <p>The provider is wired with two collaborators:</p>
     * <ul>
     *   <li>{@link #userDetailsService} via the
     *       {@link DaoAuthenticationProvider#DaoAuthenticationProvider(org.springframework.security.core.userdetails.UserDetailsService)
     *       single-argument constructor}: used to load the persisted
     *       {@code User} entity by its username column, returning a
     *       {@code UserDetails} principal whose password hash is then
     *       compared against the supplied plain-text password. The
     *       constructor-injection idiom is the canonical Spring Security
     *       6.5+ pattern; the equivalent no-arg constructor combined with
     *       {@code setUserDetailsService(...)} was deprecated in 6.5.x and
     *       is anticipated to be removed in 7.x.</li>
     *   <li>{@link #passwordEncoder() passwordEncoder()} via
     *       {@link DaoAuthenticationProvider#setPasswordEncoder}: used to
     *       perform the constant-time BCrypt verification of the supplied
     *       password against the stored hash.</li>
     * </ul>
     *
     * <p>The provider participates in the
     * {@link AuthenticationManager#authenticate(org.springframework.security.core.Authentication)}
     * dispatch flow that is registered on the {@link HttpSecurity} builder
     * by {@link #filterChain(HttpSecurity)} via
     * {@code http.authenticationProvider(authenticationProvider())}.</p>
     *
     * @return a fully-configured {@link DaoAuthenticationProvider} ready to
     *         be added to the application's authentication manager
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Exposes the framework's auto-configured
     * {@link AuthenticationManager} as a top-level Spring bean so that
     * application code (notably {@code AuthService.authenticate}) can
     * inject it directly and invoke
     * {@link AuthenticationManager#authenticate(org.springframework.security.core.Authentication)}
     * for the {@code POST /api/auth/login} flow.
     *
     * <p>The {@link AuthenticationConfiguration} parameter is supplied by
     * Spring Security's auto-configuration: it carries the assembled
     * provider list (including the {@link DaoAuthenticationProvider} bean
     * from {@link #authenticationProvider()}) and produces a
     * {@link org.springframework.security.authentication.ProviderManager}
     * that delegates to each provider in order.</p>
     *
     * <p>The method declares {@code throws Exception} to satisfy the
     * {@link AuthenticationConfiguration#getAuthenticationManager()}
     * contract; in practice this exception is unrecoverable and indicates a
     * mis-configured security context, so the application would fail to
     * start.</p>
     *
     * @param authConfig the auto-configured authentication configuration
     *                   supplied by Spring Security
     * @return the assembled {@link AuthenticationManager} for the
     *         application's authentication flows
     * @throws Exception if Spring Security cannot resolve a working
     *                   authentication manager from the configured providers
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Defines the {@link PasswordEncoder} used throughout the application
     * for password hashing (during {@code AuthService.register}) and
     * verification (during the {@link DaoAuthenticationProvider} login
     * flow).
     *
     * <p>The implementation returns a {@link BCryptPasswordEncoder}
     * constructed via its no-argument constructor, which selects the
     * framework-default work factor of 10 (~100 ms per hash on a 2025-era
     * CPU). Strength 10 is the recommended baseline for password storage
     * per the AAP §0.7.1.3; lower values weaken the hash, while higher
     * values would slow the login path under load without a corresponding
     * security gain at this threshold.</p>
     *
     * <p>The bean is declared at the {@link PasswordEncoder} interface type
     * so that consumers can inject the abstraction rather than the concrete
     * class. This keeps future migrations to alternate encoders (e.g.,
     * {@code Argon2PasswordEncoder} or
     * {@code DelegatingPasswordEncoder}) drop-in replacements without
     * touching consumer code.</p>
     *
     * @return a new {@link BCryptPasswordEncoder} with the framework
     *         default strength of 10
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Composes the application's {@link SecurityFilterChain} bean: the
     * top-level Spring Security configuration that determines, for every
     * incoming HTTP request, which filters run, in what order, and which
     * authorization rules apply.
     *
     * <p>The chain is configured as follows:</p>
     *
     * <ol>
     *   <li><b>CSRF disabled</b>. Stateless REST APIs authenticated via the
     *       {@code Authorization} header are not vulnerable to CSRF because
     *       browsers do not auto-attach the bearer token on cross-origin
     *       requests. Enabling CSRF here would force every POST/PUT/DELETE
     *       to carry a synchronizer token, which serves no security purpose
     *       in a header-authenticated API.</li>
     *   <li><b>Exception handling</b>. Authentication failures are routed
     *       to {@link AuthEntryPointJwt#commence}, which writes the
     *       canonical JSON 401 envelope.</li>
     *   <li><b>Session creation policy</b> set to
     *       {@link SessionCreationPolicy#STATELESS}: the framework neither
     *       creates nor consults an {@code HttpSession}, no
     *       {@code JSESSIONID} cookie is emitted, and the application can
     *       scale horizontally without session affinity.</li>
     *   <li><b>Authorization rules</b> evaluated in declaration order:
     *       <ul>
     *         <li>{@code /api/auth/**} &rarr; {@code permitAll()}: the
     *             registration ({@code POST /api/auth/register}) and login
     *             ({@code POST /api/auth/login}) endpoints must be
     *             reachable without a JWT.</li>
     *         <li>{@code /v3/api-docs/**}, {@code /swagger-ui/**},
     *             {@code /swagger-ui.html} &rarr; {@code permitAll()}: the
     *             OpenAPI/Swagger documentation surface remains anonymously
     *             accessible per AAP §0.1.1 user requirement #7, preserving
     *             the project's pedagogical purpose.</li>
     *         <li>{@code anyRequest()} &rarr; {@code authenticated()}:
     *             every other path (including all {@code /product/**} and
     *             {@code /student/**} endpoints) requires a valid JWT.
     *             Method-level {@code @PreAuthorize} guards on the
     *             individual controller methods then enforce role-based
     *             authorization.</li>
     *       </ul>
     *       <b>CRITICAL ordering:</b> {@code permitAll()} rules MUST be
     *       declared before {@code anyRequest().authenticated()} because
     *       Spring Security evaluates rules first-to-last and stops on the
     *       first match. Inverting the order would cause every request to
     *       fall through {@code anyRequest()} before the explicit
     *       {@code permitAll()} rules could apply.</li>
     *   <li><b>Authentication provider registration</b>. The
     *       {@link DaoAuthenticationProvider} bean from
     *       {@link #authenticationProvider()} is attached to the
     *       {@link HttpSecurity} builder, supplementing the auto-configured
     *       provider list assembled by
     *       {@link AuthenticationConfiguration}.</li>
     *   <li><b>JWT filter insertion</b>. The {@link AuthTokenFilter} bean
     *       from {@link #authenticationJwtTokenFilter()} is registered
     *       before {@link UsernamePasswordAuthenticationFilter} via
     *       {@link HttpSecurity#addFilterBefore(jakarta.servlet.Filter, Class)},
     *       which is the canonical insertion point for token-based
     *       authentication: the filter validates the bearer token and
     *       populates the {@code SecurityContextHolder} with a fully
     *       authenticated principal before the framework's
     *       {@code AuthorizationFilter} consults the
     *       {@code authorizeHttpRequests} rules.</li>
     * </ol>
     *
     * <h3>Lambda DSL discipline</h3>
     *
     * <p>All {@link HttpSecurity} configuration uses the Spring Security
     * 6.x lambda DSL ({@code http.csrf(csrf -> csrf.disable())}, etc.).
     * The pre-6.1 builder-chain style ({@code http.csrf().disable()}) has
     * been removed from the framework and is no longer compilable.</p>
     *
     * @param http the {@link HttpSecurity} builder supplied by Spring
     *             Security's auto-configuration
     * @return the assembled {@link SecurityFilterChain} bean ready to be
     *         registered with the servlet container
     * @throws Exception if any step of the {@link HttpSecurity} builder
     *                   composition fails (e.g., a malformed request matcher
     *                   pattern); such failures are unrecoverable and
     *                   prevent application startup
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF intentionally disabled: this is a stateless REST API authenticated via the
        // Authorization header (not cookies). Re-enable CSRF if browser-cookie-based JWT
        // delivery is later adopted.
        http.csrf(csrf -> csrf.disable())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            );

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
