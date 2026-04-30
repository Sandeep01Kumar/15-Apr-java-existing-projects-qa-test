package com.jspider.spring_boot_simple_crud_with_mysql.security.services;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;

/**
 * Immutable adapter that bridges the application's JPA {@link User} entity to
 * Spring Security's {@link UserDetails} contract.
 *
 * <p>This class is the value type that becomes the principal of the
 * {@code Authentication} object stored on
 * {@code SecurityContextHolder.getContext()} after a successful login. It carries
 * the user's surrogate identifier ({@code id}), unique login name
 * ({@code username}), contact address ({@code email}), the BCrypt-hashed password
 * (kept exclusively for credential verification by Spring Security's
 * {@code DaoAuthenticationProvider}), and the set of granted authorities derived
 * from the user's roles.
 *
 * <h2>Why an immutable adapter rather than annotating the entity directly</h2>
 *
 * <p>The JPA {@link User} entity carries persistence concerns (relationships,
 * lifecycle callbacks, equality semantics suitable for managed entities) that are
 * irrelevant to Spring Security's authentication contract. Splitting the two
 * concerns through this adapter:
 * <ul>
 *   <li>Decouples Spring Security from the JPA model, so future changes to the
 *       persistence layer (e.g., adding new columns or relationships to
 *       {@link User}) do not impact authentication.</li>
 *   <li>Yields a thread-safe value type. Because every field is {@code final}
 *       and the {@code authorities} collection is never mutated after
 *       construction, the same instance can be referenced safely from multiple
 *       request threads via {@code SecurityContextHolder} without any
 *       synchronization.</li>
 *   <li>Allows the {@link JsonIgnore} annotation on {@link #password} to suppress
 *       JSON serialization of the BCrypt hash without affecting the persistence
 *       layer's serialization behavior (the entity has its own
 *       {@code @ToString.Exclude} on its {@code password} field).</li>
 * </ul>
 *
 * <h2>Authority mapping and the {@code ROLE_} prefix convention</h2>
 *
 * <p>The static {@link #build(User)} factory maps each {@code Role} on the user
 * to a {@link SimpleGrantedAuthority} keyed off the enum constant's
 * {@code name()} string &mdash; e.g. {@code "ROLE_USER"} or {@code "ROLE_ADMIN"}.
 * Preserving the {@code ROLE_} prefix is mandatory because Spring Security's
 * {@code hasRole(String)} and {@code hasAnyRole(String...)} SpEL expressions
 * internally prepend the literal {@code "ROLE_"} before comparing against the
 * authorities held by the principal. The combination of (a) persisting role
 * names with the prefix in the {@code roles.name} column and (b) referencing
 * them in {@code @PreAuthorize("hasRole('USER')")} <em>without</em> the prefix
 * is the only correct configuration; any other combination would silently
 * mis-evaluate authority checks. See {@link
 * com.jspider.spring_boot_simple_crud_with_mysql.entity.ERole ERole} for the
 * canonical explanation.
 *
 * <h2>Lifecycle and consumers</h2>
 *
 * <ul>
 *   <li>{@code UserDetailsServiceImpl.loadUserByUsername} invokes
 *       {@link #build(User)} to produce a fresh {@code UserDetailsImpl} for
 *       every authentication attempt and for every JWT-bearing request once
 *       {@code AuthTokenFilter} validates the token.</li>
 *   <li>{@code DaoAuthenticationProvider.additionalAuthenticationChecks} reads
 *       the BCrypt hash through {@link #getPassword()} during the
 *       {@code POST /api/auth/login} flow and verifies it against the inbound
 *       plain-text password via {@code BCryptPasswordEncoder.matches}.</li>
 *   <li>{@code JwtUtils.generateJwtToken} casts
 *       {@code authentication.getPrincipal()} to {@code UserDetailsImpl} and
 *       reads {@link #getUsername()} to populate the JWT {@code sub} claim.</li>
 *   <li>{@code AuthService.authenticate} casts the principal to
 *       {@code UserDetailsImpl} and reads {@link #getId()},
 *       {@link #getUsername()}, {@link #getEmail()}, and
 *       {@link #getAuthorities()} to populate the {@code JwtResponse} returned
 *       to the API client.</li>
 * </ul>
 *
 * <h2>Account-status flags</h2>
 *
 * <p>All four {@code isXxxNon*()} flags ({@link #isAccountNonExpired()},
 * {@link #isAccountNonLocked()}, {@link #isCredentialsNonExpired()},
 * {@link #isEnabled()}) return the constant {@code true}. Account expiration,
 * credential expiration, account locking, and account disablement are
 * intentionally out of scope for the current authentication feature. Adding
 * any of those behaviors would require corresponding columns on the
 * {@link User} entity, additional fields on this adapter, and explicit logic
 * in the {@link #build(User)} factory; none of that is part of the present
 * iteration.
 *
 * <h2>Equality semantics</h2>
 *
 * <p>{@link #equals(Object)} and {@link #hashCode()} are computed exclusively
 * from the surrogate primary key {@link #id}. This is the canonical pattern
 * for entity-adapter classes: two {@code UserDetailsImpl} instances loaded at
 * different times for the same persisted user (same {@code id}) are considered
 * equal regardless of any field divergence (which could happen, e.g., if the
 * email was edited between loads). Field-based equality on all five fields
 * would break that natural-identity invariant.
 *
 * @see User
 * @see UserDetails
 * @see SimpleGrantedAuthority
 * @see com.jspider.spring_boot_simple_crud_with_mysql.entity.ERole
 */
public class UserDetailsImpl implements UserDetails, Serializable {

    /**
     * Serialization-compatibility marker for this {@link Serializable} type.
     * Spring Security's {@code UserDetails} contract permits implementations
     * to be serialized into HTTP sessions or other storage mechanisms; declaring
     * an explicit {@code serialVersionUID} fixes the class's serial form and
     * suppresses the IDE warning that would otherwise flag the omission.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Surrogate primary key of the underlying {@link User} entity. Boxed as
     * {@link Long} to mirror the entity's {@code id} type and to remain
     * null-tolerant for the (theoretical) edge case of a transient user not yet
     * assigned a database identifier.
     */
    private final Long id;

    /**
     * The user's unique login identifier. Returned by {@link #getUsername()}
     * for both the Spring Security authentication contract and the JWT
     * {@code sub} claim populated by {@code JwtUtils.generateJwtToken}.
     */
    private final String username;

    /**
     * The user's contact email. Surfaced to the API client via the
     * {@code JwtResponse.email} field after a successful login. Not part of
     * the {@link UserDetails} interface contract, so accessed through the
     * adapter-specific {@link #getEmail()} accessor.
     */
    private final String email;

    /**
     * The BCrypt-hashed password used by Spring Security's
     * {@code DaoAuthenticationProvider.additionalAuthenticationChecks} to
     * verify inbound plain-text credentials. The {@link JsonIgnore} annotation
     * suppresses Jackson serialization of this field if a {@code UserDetailsImpl}
     * instance is ever returned (intentionally or accidentally) from a
     * controller; the field remains accessible to Spring Security through
     * {@link #getPassword()} because Java field-level {@code @JsonIgnore} only
     * affects JSON output, not in-process accessors.
     */
    @JsonIgnore
    private final String password;

    /**
     * The collection of granted authorities derived from the user's roles by
     * {@link #build(User)}. Declared with the covariant wildcard
     * {@code Collection<? extends GrantedAuthority>} to match the exact return
     * type of {@link UserDetails#getAuthorities()} and to allow assignment of
     * a {@code List<GrantedAuthority>} (the concrete type produced by the
     * factory) without an explicit cast or copy.
     */
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * All-arguments constructor used by the static {@link #build(User)} factory
     * to instantiate an immutable adapter. The parameter order mirrors the
     * field declaration order ({@code id}, {@code username}, {@code email},
     * {@code password}, {@code authorities}).
     *
     * <p>No null-checks are performed: the caller is responsible for providing
     * fully hydrated values. Adding defensive null guards here would mask the
     * root cause of any upstream defect (e.g., a {@link User} entity that was
     * not eagerly loaded with its {@code roles}).
     *
     * @param id          the {@link User#getId() user's primary key}; may be
     *                    {@code null} only for transient (unsaved) instances
     *                    in tests
     * @param username    the unique login identifier; must be non-{@code null}
     *                    for authentication to succeed
     * @param email       the user's contact email; may be {@code null} only
     *                    in test fixtures that do not exercise the email
     *                    accessor
     * @param password    the BCrypt-hashed password; required by
     *                    {@code DaoAuthenticationProvider} during the login
     *                    flow
     * @param authorities the granted authorities collection; typically a
     *                    {@code List<GrantedAuthority>} produced by
     *                    {@link #build(User)}
     */
    public UserDetailsImpl(Long id, String username, String email, String password,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    /**
     * Static factory that adapts a fully hydrated {@link User} entity into a
     * fresh {@code UserDetailsImpl} suitable for placement on Spring Security's
     * {@code Authentication} principal.
     *
     * <p>The factory streams the user's role set into a list of
     * {@link SimpleGrantedAuthority} instances, each constructed from the
     * {@code ERole.name()} string returned by the role's enum value. Using
     * {@code .name()} (rather than {@code .toString()}) guarantees that the
     * authority string matches the enum constant's source-level identifier
     * exactly &mdash; e.g., {@code "ROLE_USER"} or {@code "ROLE_ADMIN"} &mdash;
     * which is the contract relied upon by Spring Security's
     * {@code hasRole(...)} and {@code hasAnyRole(...)} SpEL expressions.
     *
     * <p>The resulting authorities collection is the
     * {@link Collectors#toList() Collectors.toList()} default
     * ({@code ArrayList}). It is stored by reference rather than wrapped in
     * an unmodifiable view; because the factory creates a fresh list on every
     * invocation and the field is {@code final}, external mutation is
     * structurally impossible (no caller holds a reference to the list besides
     * the adapter itself).
     *
     * <p>Callers must invoke this factory inside a transactional context that
     * has already eagerly loaded {@link User#getRoles()}; the {@link User}
     * entity declares {@code FetchType.EAGER} on the relationship, so
     * {@code UserDetailsServiceImpl.loadUserByUsername} (annotated
     * {@code @Transactional}) satisfies that requirement automatically.
     *
     * @param user the persisted {@link User} to adapt; must be non-{@code null}
     *             and must have its {@code roles} set fully materialized
     * @return a freshly constructed, immutable {@code UserDetailsImpl}
     *         carrying the user's id, username, email, BCrypt-hashed password,
     *         and a granted-authority list with the {@code ROLE_} prefix
     *         preserved
     */
    public static UserDetailsImpl build(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());
        return new UserDetailsImpl(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                authorities);
    }

    /**
     * Returns the underlying {@link User} entity's surrogate primary key.
     *
     * <p>This accessor is <strong>not</strong> part of the {@link UserDetails}
     * contract; it is added because {@code AuthService.authenticate} needs to
     * populate the {@code JwtResponse.id} field after a successful login,
     * and the principal returned by {@code authentication.getPrincipal()} is
     * downcast to {@code UserDetailsImpl} for that purpose.
     *
     * @return the user's database id, or {@code null} if this adapter
     *         represents a transient (unsaved) user
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the underlying {@link User} entity's email address.
     *
     * <p>Like {@link #getId()}, this accessor sits outside the
     * {@link UserDetails} contract and exists to populate the
     * {@code JwtResponse.email} field returned by the login endpoint.
     *
     * @return the user's email address as supplied at registration time
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the granted authorities derived from the user's roles.
     *
     * <p>Each authority is a {@link SimpleGrantedAuthority} whose name matches
     * the corresponding {@code ERole} constant's source-level identifier
     * (e.g., {@code "ROLE_USER"}, {@code "ROLE_ADMIN"}). Spring Security's
     * {@code hasRole(...)} expressions strip the {@code "ROLE_"} prefix
     * internally before comparison; preserving it in the stored authority is
     * therefore mandatory for SpEL evaluation to succeed.
     *
     * @return the immutable view of the granted authorities; the underlying
     *         list is a fresh {@code ArrayList} produced by
     *         {@link #build(User)} and is never modified after construction
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * Returns the BCrypt-hashed password for credential verification.
     *
     * <p>{@code DaoAuthenticationProvider.additionalAuthenticationChecks}
     * passes this value as the {@code encodedPassword} argument to
     * {@code BCryptPasswordEncoder.matches(rawPassword, encodedPassword)}
     * during the login flow. The {@link JsonIgnore} annotation on the backing
     * field suppresses JSON serialization but does not affect this accessor's
     * visibility to Spring Security.
     *
     * @return the BCrypt hash stored on the underlying {@link User} entity
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Returns the user's unique login identifier.
     *
     * <p>Used by Spring Security's authentication infrastructure as the
     * principal name and by {@code JwtUtils.generateJwtToken} as the
     * value of the JWT {@code sub} (subject) claim.
     *
     * @return the username
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * Indicates whether the user's account has not expired.
     *
     * <p>Account expiration is out of scope for the current authentication
     * feature, so this method returns the constant {@code true}. Returning
     * {@code false} would cause Spring Security to throw
     * {@code AccountExpiredException} during authentication.
     *
     * @return {@code true} unconditionally
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user is not locked.
     *
     * <p>Account lockout (e.g., after N failed login attempts) is out of scope
     * for the current authentication feature, so this method returns the
     * constant {@code true}. Returning {@code false} would cause Spring
     * Security to throw {@code LockedException} during authentication.
     *
     * @return {@code true} unconditionally
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Indicates whether the user's credentials (password) have not expired.
     *
     * <p>Credential expiration is out of scope for the current authentication
     * feature, so this method returns the constant {@code true}. Returning
     * {@code false} would cause Spring Security to throw
     * {@code CredentialsExpiredException} during authentication.
     *
     * @return {@code true} unconditionally
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indicates whether the user is enabled.
     *
     * <p>Account disablement (the soft-delete equivalent of an
     * administrator-suspended account) is out of scope for the current
     * authentication feature, so this method returns the constant
     * {@code true}. Returning {@code false} would cause Spring Security to
     * throw {@code DisabledException} during authentication.
     *
     * @return {@code true} unconditionally
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Compares this adapter to another object for equality based exclusively on
     * the surrogate primary key {@link #id}.
     *
     * <p>Two {@code UserDetailsImpl} instances are considered equal iff they
     * are of the exact same class and their {@code id} values are equal under
     * {@link Objects#equals(Object, Object)}. This natural-identity definition
     * matches the canonical pattern for entity-adapter classes: two adapters
     * loaded at different times for the same persisted user remain equal even
     * if intermediate field updates have happened (e.g., the user changed
     * their email between loads).
     *
     * <p>Strict {@code getClass() != o.getClass()} comparison (rather than
     * {@code instanceof}) is used to avoid the asymmetric-equality trap that
     * would arise if a future subclass overrode the comparison logic.
     *
     * @param o the object to compare with this adapter
     * @return {@code true} iff {@code o} is a {@code UserDetailsImpl} with the
     *         same {@code id}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDetailsImpl user = (UserDetailsImpl) o;
        return Objects.equals(id, user.id);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}, computed
     * exclusively from the {@link #id} field.
     *
     * @return the hash code derived from {@code id}
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
