package com.jspider.spring_boot_simple_crud_with_mysql.security.jwt;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jspider.spring_boot_simple_crud_with_mysql.security.services.UserDetailsServiceImpl;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security per-request servlet filter that bridges the gap between
 * incoming JWT-bearing HTTP requests and the framework's authorization
 * pipeline. The filter runs exactly once for every request handled by the
 * application's embedded Tomcat instance and is responsible for transforming
 * a raw {@code Authorization: Bearer <token>} header value into a fully
 * authenticated {@link UsernamePasswordAuthenticationToken} stored on the
 * thread-local {@link SecurityContextHolder} for the duration of the request.
 *
 * <h2>Position in the security filter chain</h2>
 *
 * <p>{@code AuthTokenFilter} is registered exactly once by
 * {@code SecurityConfig.filterChain(HttpSecurity)} via the
 * {@code addFilterBefore(authenticationJwtTokenFilter(),
 * UsernamePasswordAuthenticationFilter.class)} call &mdash; that is, the
 * filter executes <em>before</em> Spring Security's built-in
 * {@code UsernamePasswordAuthenticationFilter}, before the
 * {@code AuthorizationFilter} that evaluates the
 * {@code authorizeHttpRequests(...)} rules, and before any controller's
 * {@code @PreAuthorize} guard. By the time the request reaches the
 * authorization filter, this filter has already either populated
 * {@link SecurityContextHolder} with the authenticated principal or left it
 * empty, and the downstream authorization decision proceeds against that
 * context.
 *
 * <h2>Single registration discipline (no {@code @Component} annotation)</h2>
 *
 * <p>This class is deliberately <strong>not</strong> annotated with
 * {@link org.springframework.stereotype.Component @Component} or any other
 * stereotype annotation. The single, intentional registration point for the
 * filter is the {@code @Bean} method
 * {@code SecurityConfig.authenticationJwtTokenFilter()}, which constructs a
 * fresh instance via {@code new AuthTokenFilter()} and lets the framework
 * post-process the bean to satisfy its {@link Autowired @Autowired} fields.
 * Adding {@code @Component} would cause Spring Boot's default servlet-filter
 * auto-registration to wire the same instance into the global filter chain
 * via a synthesized {@code FilterRegistrationBean} <em>in addition to</em>
 * the security filter chain, with two undesirable consequences:
 * <ul>
 *   <li>Every authenticated request would pay the JWT-validation cost twice
 *       (one HMAC verification per filter invocation) and trigger the
 *       {@code UserDetailsServiceImpl.loadUserByUsername} database lookup
 *       twice.</li>
 *   <li>The {@link SecurityContextHolder} would be populated twice for the
 *       same request, which while logically idempotent is wasteful and
 *       subtly obscures the request flow during diagnostic tracing.</li>
 * </ul>
 *
 * <h2>Why {@link OncePerRequestFilter}</h2>
 *
 * <p>Extending {@link OncePerRequestFilter} guarantees that
 * {@link #doFilterInternal} executes <em>exactly once</em> per HTTP request,
 * even when Spring's request-dispatching mechanism internally forwards a
 * request (for example, from a controller to an error page registered under
 * a separate dispatcher type). The base class tracks a per-request marker
 * attribute and short-circuits subsequent invocations within the same
 * request. Plain {@code Filter} would not provide this guarantee and could
 * cause this filter to run multiple times on a single logical request,
 * doubling JWT validation work and double-populating the
 * {@link SecurityContextHolder}.
 *
 * <h2>Robustness contract: never break the chain</h2>
 *
 * <p>The implementation strictly adheres to a single robustness invariant:
 * regardless of any condition encountered &mdash; missing
 * {@code Authorization} header, malformed header value, unsupported
 * authentication scheme, invalid JWT signature, expired JWT, principal
 * lookup failure, or any unexpected runtime exception &mdash; the filter
 * <strong>always</strong> calls
 * {@link FilterChain#doFilter(jakarta.servlet.ServletRequest,
 * jakarta.servlet.ServletResponse) filterChain.doFilter(request, response)}
 * to advance the request to the next filter. Throwing or returning early
 * would break the public {@code /api/auth/**} routes (which legitimately
 * arrive with no token) and any path explicitly marked
 * {@code permitAll()} in {@code SecurityConfig}.
 *
 * <p>To enforce the invariant, the {@code filterChain.doFilter} call is
 * positioned <em>outside</em> the {@code try/catch} block that wraps the
 * authentication-extraction logic. Any exception from JWT parsing, principal
 * loading, or {@link SecurityContextHolder} population is caught, logged at
 * ERROR level, and discarded; the request then proceeds to the next filter
 * with an empty security context. The downstream
 * {@code AuthorizationFilter} then decides whether to allow the request
 * (for {@code permitAll()} paths) or to respond with HTTP 401 (for
 * {@code authenticated()} paths) by triggering {@code AuthEntryPointJwt}.
 *
 * <h2>Stateless authentication contract</h2>
 *
 * <p>Per the application's {@code SessionCreationPolicy.STATELESS}
 * configuration, no {@code HttpSession} is created or consulted by this
 * filter. Every protected request must independently re-authenticate by
 * presenting its own JWT in the {@code Authorization} header; there is no
 * cookie-based session continuity. The principal populated on the
 * {@link SecurityContextHolder} is automatically cleared at the end of the
 * request by Spring's {@code SecurityContextHolderFilter} (which runs
 * earlier in the chain), so this filter only ever observes an empty
 * security context at entry and is responsible solely for adding
 * authentication on success &mdash; never for clearing it on failure.
 *
 * <h2>Thread safety</h2>
 *
 * <p>The filter is registered as a Spring singleton via the {@code @Bean}
 * declaration in {@code SecurityConfig} and is therefore shared across all
 * request-handling threads. Its safety under concurrent invocation rests on
 * three invariants:
 * <ul>
 *   <li>The two injected collaborator fields ({@link #jwtUtils} and
 *       {@link #userDetailsService}) are populated once at startup by
 *       Spring's bean post-processor and are never reassigned.</li>
 *   <li>{@link #doFilterInternal} allocates only request-scoped local
 *       variables &mdash; the JWT string, the username, the
 *       {@link UserDetails}, and the
 *       {@link UsernamePasswordAuthenticationToken} &mdash; none of which
 *       escape the method body.</li>
 *   <li>{@link SecurityContextHolder} uses a {@code ThreadLocal} (the
 *       framework's default {@code MODE_THREADLOCAL} strategy) to isolate
 *       authentication state per request thread, so concurrent invocations
 *       cannot observe each other's principals.</li>
 * </ul>
 *
 * <h2>Coordination with {@code JwtUtils} and {@code UserDetailsServiceImpl}</h2>
 *
 * <p>The filter delegates all JJWT-specific concerns &mdash; signature
 * verification, expiration checking, subject extraction &mdash; to
 * {@link JwtUtils#validateJwtToken(String)} and
 * {@link JwtUtils#getUserNameFromJwtToken(String)}. It delegates JPA-to-{@link
 * UserDetails} adaptation to
 * {@link UserDetailsServiceImpl#loadUserByUsername(String)}. This separation
 * keeps the filter lean and lets the JWT and persistence layers evolve
 * independently of the authentication-pipeline orchestration here.
 *
 * @see OncePerRequestFilter
 * @see JwtUtils
 * @see UserDetailsServiceImpl
 * @see SecurityContextHolder
 * @see UsernamePasswordAuthenticationToken
 * @see WebAuthenticationDetailsSource
 */
public class AuthTokenFilter extends OncePerRequestFilter {

    /**
     * SLF4J logger used to emit ERROR-level diagnostic messages from the
     * blanket {@code catch (Exception e)} block of {@link #doFilterInternal}.
     *
     * <p>The logger is declared {@code private static final} so it is shared
     * by every instance of the class (which, as a Spring singleton, is one)
     * and is initialized exactly once at class-loading time. The logger
     * category resolves to the fully qualified name of {@code AuthTokenFilter},
     * allowing operators to tune its threshold independently in
     * {@code logback-spring.xml} or via the {@code logging.level} family of
     * Spring properties.
     *
     * <p>ERROR is the appropriate level here because every log emission from
     * this site corresponds to a failed authentication attempt (malformed
     * token, expired token, signature mismatch, principal lookup failure)
     * that operators may need to investigate. Lower levels (INFO, DEBUG)
     * would either flood production logs with routine traffic or hide
     * genuine security events from default-configured monitoring tools.
     */
    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);

    /**
     * Spring-managed {@link JwtUtils} component used to validate the
     * cryptographic integrity and expiration of the bearer token, and to
     * extract the {@code sub} (username) claim once validation succeeds.
     *
     * <p>Field injection via {@link Autowired @Autowired} is the canonical
     * Spring Security 6.x pattern for {@link OncePerRequestFilter}
     * subclasses that are registered through a {@code @Bean} factory method
     * rather than via a stereotype scan. The framework's
     * {@code AutowiredAnnotationBeanPostProcessor} populates the field after
     * {@code SecurityConfig.authenticationJwtTokenFilter()} returns the new
     * filter instance, so by the time the filter receives its first request
     * the dependency is fully resolved. Constructor injection would be an
     * equivalent alternative but adds boilerplate without functional
     * benefit, and the AAP §0.7.1.1 explicitly carves out an exception for
     * Spring Security filters that follow this idiom.
     */
    @Autowired
    private JwtUtils jwtUtils;

    /**
     * Spring-managed {@link UserDetailsServiceImpl} used to resolve the
     * username extracted from the verified JWT into a fully hydrated
     * {@link UserDetails} principal. The lookup performs a single indexed
     * {@code SELECT ... FROM users WHERE username = ?} (with the eagerly
     * fetched {@code user_roles} join) inside a read-only transactional
     * context.
     *
     * <p>The field is typed against the concrete
     * {@link UserDetailsServiceImpl} class (rather than the
     * {@link org.springframework.security.core.userdetails.UserDetailsService
     * UserDetailsService} interface) for two reasons:
     * <ul>
     *   <li><strong>Bean disambiguation.</strong> If a future feature
     *       introduces an alternate {@code UserDetailsService} implementation
     *       (e.g., a remote LDAP-backed service for a separate authentication
     *       flow), typing this field against the interface would force
     *       Spring to require an explicit {@code @Qualifier}; typing against
     *       the concrete class side-steps the ambiguity.</li>
     *   <li><strong>Symmetry with the AAP.</strong> The Agent Action Plan
     *       §0.5.1.4 specifies the field type as {@code UserDetailsServiceImpl},
     *       and this implementation honors that specification verbatim.</li>
     * </ul>
     */
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    /**
     * Inspects every incoming HTTP request, extracts the JWT from the
     * {@code Authorization: Bearer <token>} header (if present), validates
     * the token via {@link JwtUtils#validateJwtToken(String)}, loads the
     * corresponding principal via
     * {@link UserDetailsServiceImpl#loadUserByUsername(String)}, and
     * populates the {@link SecurityContextHolder} with a fully authenticated
     * {@link UsernamePasswordAuthenticationToken} so that downstream
     * authorization can succeed.
     *
     * <h3>Algorithm</h3>
     *
     * <ol>
     *   <li>Invoke {@link #parseJwt(HttpServletRequest)} to extract the raw
     *       JWT string from the {@code Authorization} header. The helper
     *       returns {@code null} when the header is missing, blank, or does
     *       not start with the {@code "Bearer "} prefix.</li>
     *   <li>If a JWT is present <em>and</em> {@link
     *       JwtUtils#validateJwtToken(String)} returns {@code true},
     *       extract the username via
     *       {@link JwtUtils#getUserNameFromJwtToken(String)}.</li>
     *   <li>Resolve the username into a {@link UserDetails} via
     *       {@link UserDetailsServiceImpl#loadUserByUsername(String)}. A
     *       {@code UsernameNotFoundException} from this step is caught by
     *       the surrounding {@code try/catch} and logged.</li>
     *   <li>Construct a fully authenticated
     *       {@link UsernamePasswordAuthenticationToken} using the three-arg
     *       constructor:
     *       <ul>
     *         <li>{@code principal = userDetails}: the loaded principal,
     *             whose {@code getPrincipal()} can be downcast to
     *             {@link com.jspider.spring_boot_simple_crud_with_mysql.security.services.UserDetailsImpl}
     *             by any downstream component that needs richer accessors.</li>
     *         <li>{@code credentials = null}: the password is intentionally
     *             omitted because the JWT signature verification has already
     *             established the authenticity of the request &mdash; the
     *             token <em>is</em> the credential.</li>
     *         <li>{@code authorities = userDetails.getAuthorities()}: the
     *             granted-authority list (e.g., {@code ROLE_USER},
     *             {@code ROLE_ADMIN}) used by Spring Security's
     *             {@code hasRole(...)} and {@code hasAnyRole(...)} SpEL
     *             expressions evaluated by the
     *             {@code @EnableMethodSecurity}-driven
     *             {@code @PreAuthorize} interceptors on the
     *             {@code ProductController} and {@code StudentController}
     *             endpoints.</li>
     *       </ul>
     *       The three-arg constructor flags the resulting token as already
     *       authenticated; the two-arg form is for unauthenticated tokens
     *       that have yet to be processed by an
     *       {@code AuthenticationProvider}.</li>
     *   <li>Attach a {@link
     *       org.springframework.security.web.authentication.WebAuthenticationDetails
     *       WebAuthenticationDetails} object via
     *       {@link UsernamePasswordAuthenticationToken#setDetails(Object)}.
     *       This object captures the request's remote IP address and any
     *       {@code HttpSession} ID, which downstream consumers (including
     *       audit logging, IP-based access controls if added later, and the
     *       built-in event publication infrastructure) may inspect.</li>
     *   <li>Persist the authenticated token on the per-thread security
     *       context via
     *       {@link SecurityContextHolder#getContext()}{@code .setAuthentication(authentication)}.
     *       The token remains in scope for the duration of the request and
     *       is automatically cleared when the request completes.</li>
     * </ol>
     *
     * <h3>Error handling</h3>
     *
     * <p>The entire authentication-extraction logic is wrapped in a single
     * {@code try/catch (Exception e)} block. Any exception &mdash;
     * {@link io.jsonwebtoken.MalformedJwtException},
     * {@link io.jsonwebtoken.ExpiredJwtException},
     * {@link io.jsonwebtoken.UnsupportedJwtException},
     * {@code io.jsonwebtoken.security.SignatureException},
     * {@link IllegalArgumentException},
     * {@link org.springframework.security.core.userdetails.UsernameNotFoundException
     * UsernameNotFoundException}, or any unexpected
     * {@link RuntimeException} &mdash; is caught, logged at ERROR level
     * with the exception message (but no stack trace, to keep production
     * logs readable), and discarded. The {@link SecurityContextHolder} is
     * left empty, and the downstream
     * {@code AuthorizationFilter} subsequently triggers
     * {@code AuthEntryPointJwt.commence(...)} for paths that require
     * authentication, returning HTTP 401 to the client.
     *
     * <h3>Always-proceed guarantee</h3>
     *
     * <p>The {@code filterChain.doFilter(request, response)} call sits
     * <em>outside</em> the {@code try/catch} block, guaranteeing that the
     * request always advances to the next filter in the chain regardless of
     * what happened during authentication extraction. This is critical for
     * two reasons:
     * <ul>
     *   <li>Public paths (notably {@code /api/auth/register} and
     *       {@code /api/auth/login}) legitimately arrive without a JWT, and
     *       must be allowed to proceed for the
     *       {@code AuthController} to handle them.</li>
     *   <li>Internal forwards (e.g., to error pages) must not be silently
     *       swallowed by an exception in this filter; positioning the
     *       chain advancement outside the {@code try/catch} ensures clean
     *       propagation.</li>
     * </ul>
     *
     * @param request the inbound HTTP request, source of the
     *                {@code Authorization} header and of the remote
     *                connection details captured by
     *                {@link WebAuthenticationDetailsSource}; must be
     *                non-{@code null} per the Servlet API contract
     * @param response the outbound HTTP response, passed through unchanged
     *                 to the downstream filter; must be non-{@code null}
     *                 per the Servlet API contract
     * @param filterChain the remaining filter chain that this filter must
     *                    advance the request through; must be non-{@code null}
     *                    per the Servlet API contract
     * @throws ServletException if a downstream filter raises a
     *                          {@code ServletException} from
     *                          {@link FilterChain#doFilter}; not thrown by
     *                          this method's own logic, which catches all
     *                          exceptions internally
     * @throws IOException if a downstream filter raises an
     *                     {@link IOException} from
     *                     {@link FilterChain#doFilter}; not thrown by this
     *                     method's own logic, which catches all exceptions
     *                     internally
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT string from the request's
     * {@code Authorization: Bearer <token>} header, or returns {@code null}
     * if the header is absent, blank, or does not use the Bearer scheme.
     *
     * <h3>Header lookup</h3>
     *
     * <p>The method calls {@link HttpServletRequest#getHeader(String)} with
     * the literal {@code "Authorization"} (capital {@code A}, not
     * {@code "authorization"}). Header name lookups are case-insensitive
     * per RFC 7230 §3.2 and the Jakarta Servlet 6.0 contract, so the
     * casing here is purely a stylistic choice that aligns with the
     * canonical RFC 7235 spelling.
     *
     * <h3>Prefix validation</h3>
     *
     * <p>The header value is accepted only when it satisfies two
     * conditions:
     * <ol>
     *   <li>{@link StringUtils#hasText(String)} returns {@code true} &mdash;
     *       the header is non-{@code null}, non-empty, and not composed
     *       entirely of whitespace. Spring's {@code StringUtils.hasText} is
     *       preferred over a hand-rolled
     *       {@code header != null && !header.trim().isEmpty()} check
     *       because it correctly handles all Unicode whitespace categories
     *       and is the framework-blessed null-safe text test.</li>
     *   <li>The value starts with the seven-character literal
     *       {@code "Bearer "} (six letters of the scheme name plus the
     *       single trailing space mandated by RFC 6750 §2.1). A header
     *       value of {@code "Bearer"} without a trailing space, or
     *       {@code "Basic Zm9vOmJhcg=="}, or any other scheme correctly
     *       fails the prefix check and yields a {@code null} return.</li>
     * </ol>
     *
     * <p>When both conditions hold, {@link String#substring(int)} with
     * argument 7 strips the leading {@code "Bearer "} prefix and returns
     * the raw token suffix, which can then be passed directly to
     * {@link JwtUtils#validateJwtToken(String)} and
     * {@link JwtUtils#getUserNameFromJwtToken(String)}. The seven-character
     * offset is the length of {@code "Bearer "} (including the trailing
     * space), <em>not</em> the length of the scheme name alone.
     *
     * <h3>Hardcoded header name</h3>
     *
     * <p>The header name is intentionally hardcoded as {@code "Authorization"}
     * rather than read from the {@code jwt.header} configuration property.
     * The {@code Authorization} header is part of the HTTP standard
     * (RFC 7235), so configurability here would invite mis-configuration
     * with no realistic benefit. The {@code jwt.header} property exists
     * only as a documentation aid in {@code application.properties} for
     * operators reading the configuration; it is intentionally not
     * propagated into the filter's wire protocol.
     *
     * <h3>Return-{@code null} convention</h3>
     *
     * <p>Returning {@code null} for missing or non-Bearer headers (rather
     * than throwing an exception or returning an empty string) lets the
     * caller {@link #doFilterInternal} use the idiomatic null-check
     * {@code if (jwt != null && jwtUtils.validateJwtToken(jwt))} to
     * short-circuit the entire authentication extraction with a single
     * predicate. Throwing here would force the caller to wrap the call in
     * its own {@code try/catch} block and would conflate the structural
     * concern (no token in the request) with the cryptographic concern
     * (token present but invalid).
     *
     * @param request the inbound HTTP request whose
     *                {@code Authorization} header is to be inspected; must
     *                be non-{@code null} per the Servlet API contract
     * @return the raw JWT string with the {@code "Bearer "} prefix stripped,
     *         or {@code null} if the header is absent, blank, or uses a
     *         non-Bearer authentication scheme
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
