package com.jspider.spring_boot_simple_crud_with_mysql.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.UserRepository;

/**
 * Spring Security {@link UserDetailsService} implementation that bridges the
 * JPA persistence layer and the framework's authentication contract.
 *
 * <p>This service is the single integration point through which Spring Security
 * resolves a username supplied at login time (or carried in a JWT subject claim
 * on a subsequent authenticated request) into the fully hydrated
 * {@link UserDetails} object that the rest of the security pipeline consumes.
 * The implementation deliberately mirrors the canonical Spring Security
 * reference architecture: a single {@code @Service} bean, a single derived
 * repository call, and a single delegation to the {@link UserDetailsImpl#build
 * UserDetailsImpl.build} static factory that adapts the JPA entity into a
 * Spring Security principal.
 *
 * <h2>Position in the authentication pipeline</h2>
 *
 * <p>Two distinct Spring Security collaborators invoke
 * {@link #loadUserByUsername(String)} during the JWT-based authentication
 * workflow:
 *
 * <ul>
 *   <li><strong>Login flow</strong>
 *       ({@code POST /api/auth/login}): {@code AuthService.authenticate}
 *       delegates credential verification to
 *       {@code AuthenticationManager.authenticate}, which in turn invokes
 *       {@code DaoAuthenticationProvider.retrieveUser}. That provider was
 *       configured by {@code SecurityConfig} with this class as its
 *       {@link UserDetailsService}, so the provider calls back into
 *       {@link #loadUserByUsername(String)} to obtain the {@link UserDetails}
 *       whose {@link UserDetails#getPassword() password hash} is then
 *       compared, via {@code BCryptPasswordEncoder.matches}, against the
 *       plain-text password supplied in the {@code LoginRequest} payload.</li>
 *   <li><strong>Per-request validation flow</strong> (every authenticated
 *       request to {@code /product/*}, {@code /student/*}, or any other
 *       protected endpoint): {@code AuthTokenFilter} extracts the username
 *       from the verified JWT's {@code sub} claim and calls
 *       {@link #loadUserByUsername(String)} directly to populate
 *       {@code SecurityContextHolder} with a fully authenticated
 *       {@code UsernamePasswordAuthenticationToken}, allowing downstream
 *       {@code @PreAuthorize} expressions to evaluate against the user's
 *       authorities.</li>
 * </ul>
 *
 * <h2>Stateless authentication and session-less semantics</h2>
 *
 * <p>The application's {@code SecurityFilterChain} configures
 * {@code SessionCreationPolicy.STATELESS}; no {@code HttpSession} is created,
 * and no per-user state is cached between requests. This service therefore
 * runs on every authenticated request, executing exactly one
 * {@code SELECT ... FROM users WHERE username = ?} (with the eagerly fetched
 * {@code user_roles} join) and returning a fresh {@link UserDetailsImpl}
 * instance each time. The {@code users.username} column carries a database-level
 * unique index established by the {@code @UniqueConstraint(columnNames =
 * "username")} declaration on the {@link User} entity, so the lookup runs in
 * {@code O(log n)} time even under heavy authenticated traffic.
 *
 * <h2>Transactional boundary</h2>
 *
 * <p>The {@link #loadUserByUsername(String)} method is annotated with
 * Spring's {@link Transactional @Transactional}, opening a single read-only
 * transactional context that spans the {@link UserRepository#findByUsername
 * findByUsername} call <em>and</em> the subsequent
 * {@link UserDetailsImpl#build(User) UserDetailsImpl.build} invocation.
 * Although {@link User#getRoles()} is mapped with
 * {@code @ManyToMany(fetch = FetchType.EAGER)}, the eager fetch is materialized
 * by Hibernate within the same {@code Session} that performed the
 * {@code SELECT}; if the session were closed before
 * {@code UserDetailsImpl.build} iterated over the role collection, Hibernate
 * would emit a {@code LazyInitializationException} on the first access. The
 * transactional boundary established here guarantees the session remains open
 * through the role-mapping step and closes cleanly once
 * {@link UserDetailsImpl} has been constructed.
 *
 * <p>The annotation explicitly references
 * {@link org.springframework.transaction.annotation.Transactional Spring's
 * declarative transaction annotation} rather than
 * {@code jakarta.transaction.Transactional}; only Spring's variant supports
 * the {@code propagation}, {@code isolation}, {@code readOnly}, and
 * {@code rollbackFor} attributes that the framework's transaction
 * infrastructure recognizes.
 *
 * <h2>Failure semantics</h2>
 *
 * <p>When the supplied username does not resolve to a persisted {@link User}
 * row, {@link #loadUserByUsername(String)} throws a
 * {@link UsernameNotFoundException} with the canonical Spring Security
 * message format {@code "User Not Found with username: " + username}. Each
 * downstream consumer interprets that exception according to its role:
 *
 * <ul>
 *   <li>{@code DaoAuthenticationProvider}, when configured with the
 *       default {@code hideUserNotFoundExceptions = true} setting, wraps the
 *       {@code UsernameNotFoundException} in a generic
 *       {@code BadCredentialsException} so that the API client cannot
 *       distinguish "user does not exist" from "wrong password" through
 *       timing-side-channel analysis. The
 *       {@code GlobalExceptionHandler.handleBadCredentials} handler then
 *       surfaces the failure as HTTP 401.</li>
 *   <li>{@code AuthTokenFilter} catches the exception, logs the failure at
 *       {@code ERROR} level (without leaking the failed username to the
 *       client), and proceeds without setting an authentication on
 *       {@code SecurityContextHolder}; subsequent
 *       {@code AuthorizationFilter} evaluation of {@code anyRequest()
 *       .authenticated()} then triggers
 *       {@code AuthEntryPointJwt.commence}, which writes the structured
 *       HTTP 401 response.</li>
 *   <li>The {@code GlobalExceptionHandler.handleUserNotFound} handler is
 *       reachable only on the rare paths where the exception escapes the
 *       Spring Security filter chain; it produces an HTTP 404 response
 *       carrying the same message format declared here.</li>
 * </ul>
 *
 * <h2>Component-scan registration</h2>
 *
 * <p>The {@link Service @Service} stereotype annotation registers this class
 * as a Spring-managed bean discoverable by the auto-configured component scan
 * rooted at the application's base package
 * {@code com.jspider.spring_boot_simple_crud_with_mysql}. Spring resolves the
 * bean by the {@link UserDetailsService} type when constructing
 * {@code DaoAuthenticationProvider} in {@code SecurityConfig}, and by the
 * concrete {@code UserDetailsServiceImpl} type when {@code AuthTokenFilter}
 * declares an {@code @Autowired} field of that exact type. Removing
 * {@code @Service} would cause Spring to silently omit this bean from the
 * context and the {@code DaoAuthenticationProvider} configuration would fail
 * with {@code NoSuchBeanDefinitionException} at startup.
 *
 * @see UserDetails
 * @see UserDetailsService
 * @see UsernameNotFoundException
 * @see UserDetailsImpl
 * @see User
 * @see UserRepository
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    /**
     * Spring Data JPA repository for the {@link User} entity, used to
     * resolve a supplied username into a persisted user row.
     *
     * <p>Field injection through {@link Autowired @Autowired} is intentional:
     * it follows the canonical Spring Security {@link UserDetailsService}
     * pattern documented in the framework's reference architecture and in
     * the project's Agent Action Plan §0.7.1.1. While constructor injection
     * is the broader Spring 6.x recommendation, the field-injection idiom is
     * preserved here for consistency with established Spring Security
     * tutorials and to keep the {@link UserDetailsServiceImpl} declaration
     * minimal &mdash; a single annotated field, a single contract method,
     * and zero hand-written constructors.
     *
     * <p>The injected proxy is the runtime {@code SimpleJpaRepository}
     * implementation generated by Spring Data at startup; it is thread-safe
     * and may be invoked from any number of concurrent request threads
     * without external synchronization. The proxy's
     * {@link UserRepository#findByUsername findByUsername} method translates
     * to the JPQL query {@code SELECT u FROM User u WHERE u.username =
     * :username}, which Hibernate compiles into the SQL statement
     * {@code SELECT id, username, email, password FROM users WHERE username
     * = ?}; the {@code @ManyToMany(fetch = FetchType.EAGER)} declaration on
     * {@link User#getRoles() User.roles} causes the eager join across
     * {@code user_roles} and {@code roles} to be issued in the same
     * round-trip.
     */
    @Autowired
    private UserRepository userRepository;

    /**
     * Resolves the supplied username into a fully hydrated
     * {@link UserDetails} principal for use by Spring Security's
     * authentication pipeline.
     *
     * <p>The implementation performs exactly two steps:
     *
     * <ol>
     *   <li>Invokes {@link UserRepository#findByUsername(String)} to retrieve
     *       the {@link User} entity matching the supplied username, throwing
     *       {@link UsernameNotFoundException} via
     *       {@link java.util.Optional#orElseThrow(java.util.function.Supplier)
     *       Optional.orElseThrow} when the lookup yields an empty
     *       {@link java.util.Optional}. The lambda form
     *       {@code () -> new UsernameNotFoundException(...)} is used (rather
     *       than the no-argument {@code orElseThrow()}) so that the exception
     *       carries the canonical Spring Security message format
     *       {@code "User Not Found with username: " + username}, which the
     *       downstream {@code GlobalExceptionHandler} relies on for diagnostic
     *       logging.</li>
     *   <li>Delegates the JPA-to-{@link UserDetails} adaptation to the static
     *       factory {@link UserDetailsImpl#build(User)}. The factory streams
     *       the user's {@link User#getRoles() role set} into a list of
     *       {@code SimpleGrantedAuthority} instances, each keyed off the
     *       enum constant's {@code name()} string &mdash; preserving the
     *       {@code ROLE_} prefix that Spring Security's {@code hasRole(...)}
     *       and {@code hasAnyRole(...)} SpEL expressions expect. Using the
     *       static factory rather than constructing
     *       {@link UserDetailsImpl#UserDetailsImpl(Long, String, String,
     *       String, java.util.Collection) the all-args constructor} directly
     *       centralizes the role-mapping logic at a single call site,
     *       avoiding duplication in {@code AuthTokenFilter} and any future
     *       caller that needs the same adaptation.</li>
     * </ol>
     *
     * <h3>Why {@link Transactional @Transactional} is mandatory</h3>
     *
     * <p>The {@link User#getRoles() roles} association is declared with
     * {@code @ManyToMany(fetch = FetchType.EAGER)}, but the eager fetch is
     * realized by Hibernate against the {@code Session} that executed the
     * underlying {@code SELECT}; that session must remain open while
     * {@link UserDetailsImpl#build(User)} iterates over the collection. The
     * {@link Transactional @Transactional} annotation establishes the
     * required boundary: Spring's
     * {@code TransactionInterceptor} opens a read transaction before the
     * {@link UserRepository#findByUsername findByUsername} call, the
     * Hibernate {@code Session} stays bound to the current thread for the
     * duration of the method, and the role iteration in
     * {@link UserDetailsImpl#build(User)} therefore proceeds against a
     * still-open persistence context. Without the annotation, the
     * implementation would be vulnerable to
     * {@code LazyInitializationException} under any Spring Boot
     * configuration that closes the auto-applied session prior to method
     * return.
     *
     * <h3>Transaction propagation</h3>
     *
     * <p>The annotation uses default {@link
     * org.springframework.transaction.annotation.Propagation#REQUIRED
     * REQUIRED} propagation: if a transaction is already active on the
     * calling thread (e.g., when invoked indirectly from the
     * {@code AuthService.authenticate} flow that itself runs inside a
     * higher-level transactional boundary), this method joins it; otherwise
     * a fresh transaction is started for the duration of the call. The
     * default isolation level is honored, and the transaction is
     * automatically committed (or rolled back, on exception) when the method
     * returns.
     *
     * <h3>Idempotency and concurrency</h3>
     *
     * <p>The method is read-only and idempotent: it executes a single
     * indexed {@code SELECT} and constructs a fresh {@link UserDetailsImpl}
     * value object on every invocation. No shared mutable state is touched,
     * and the method can therefore be invoked safely from any number of
     * concurrent request threads. No caching layer is interposed; per the
     * Agent Action Plan §0.7.1.3, role-change invalidation would require
     * additional infrastructure that is intentionally out of scope for the
     * current authentication feature.
     *
     * @param username the {@code users.username} column value to resolve;
     *                 supplied either by {@code DaoAuthenticationProvider}
     *                 from the {@code LoginRequest} payload during the
     *                 {@code POST /api/auth/login} flow, or by
     *                 {@code AuthTokenFilter} from the verified JWT's
     *                 {@code sub} claim during per-request authentication;
     *                 expected to be non-{@code null} and non-blank, although
     *                 a {@code null} or blank value simply yields an empty
     *                 {@link java.util.Optional} from
     *                 {@link UserRepository#findByUsername findByUsername}
     *                 and surfaces as the standard
     *                 {@link UsernameNotFoundException}
     * @return a freshly constructed {@link UserDetailsImpl} carrying the
     *         user's id, username, email, BCrypt-hashed password, and the
     *         granted-authority list derived from the user's role set;
     *         never {@code null}
     * @throws UsernameNotFoundException if no row exists in the {@code users}
     *                                   table matching the supplied
     *                                   {@code username}; the exception
     *                                   message is the canonical Spring
     *                                   Security format
     *                                   {@code "User Not Found with
     *                                   username: " + username}
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));
        return UserDetailsImpl.build(user);
    }
}
