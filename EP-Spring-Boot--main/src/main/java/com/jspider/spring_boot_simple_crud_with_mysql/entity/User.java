package com.jspider.spring_boot_simple_crud_with_mysql.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * JPA entity representing an authenticated end user of the application.
 *
 * <p>Each row of the {@code users} table corresponds to a single principal that can
 * register through {@code POST /api/auth/register}, authenticate through
 * {@code POST /api/auth/login}, and subsequently access the protected
 * {@code /product/*} and {@code /student/*} endpoints by presenting a JWT in the
 * {@code Authorization: Bearer &lt;token&gt;} header. The entity is the canonical
 * persistence-layer representation of the user identity domain introduced by the
 * JWT-based authentication and authorization feature; it is never serialized
 * directly to clients (the API contract is mediated through dedicated request and
 * response DTOs in the {@code payload} package), and its state is exclusively
 * mutated through the {@code AuthService} registration workflow.
 *
 * <h2>Persistence mapping</h2>
 *
 * <ul>
 *   <li>The {@code id} field is the surrogate primary key, typed as {@link Long}
 *       (mapped to {@code BIGINT} on the database side) so that the user population
 *       can grow well beyond the 32-bit range. The
 *       {@link GenerationType#IDENTITY} strategy delegates auto-increment to the
 *       underlying database column and aligns with the {@code UserRepository}
 *       declaration {@code JpaRepository<User, Long>}.</li>
 *   <li>The {@code username} column is a {@code VARCHAR(20) NOT NULL UNIQUE}
 *       string that uniquely identifies the principal during login. The unique
 *       constraint is declared at the table level (via
 *       {@link UniqueConstraint @UniqueConstraint}) so that Hibernate generates a
 *       database-level unique index, which is performance-critical because every
 *       authenticated request results in a {@code findByUsername} lookup through
 *       {@code UserDetailsServiceImpl.loadUserByUsername}.</li>
 *   <li>The {@code email} column is a {@code VARCHAR(50) NOT NULL UNIQUE} string
 *       carrying the contact address supplied at registration time. The unique
 *       constraint enforces the one-account-per-email invariant relied upon by
 *       {@code UserRepository.existsByEmail}.</li>
 *   <li>The {@code password} column is a {@code VARCHAR(120) NOT NULL} string
 *       holding the BCrypt hash produced by {@code BCryptPasswordEncoder.encode}.
 *       The 120-character ceiling comfortably accommodates BCrypt's 60-character
 *       output (e.g., {@code $2a$10$...}) plus headroom for future encoder
 *       upgrades. Plain-text passwords are never persisted; the encoded form is
 *       computed in {@code AuthService.register} prior to {@code save}.</li>
 *   <li>The {@code roles} field models the many-to-many relationship between
 *       users and {@link Role roles} through the {@code user_roles} join table
 *       declared via {@link JoinTable @JoinTable}. The
 *       {@link FetchType#EAGER EAGER} fetch type is mandatory: at authentication
 *       time {@code UserDetailsImpl.build(User)} maps the role set to the
 *       {@code SimpleGrantedAuthority} collection that backs Spring Security's
 *       {@code hasRole} and {@code hasAnyRole} expressions. With
 *       {@link FetchType#LAZY LAZY} fetching, accessing {@code getRoles()} after
 *       the originating transaction has closed would raise
 *       {@code LazyInitializationException}; the eager strategy avoids that
 *       failure mode at the cost of a single additional join, which is acceptable
 *       because the {@code user_roles} cardinality is small.</li>
 * </ul>
 *
 * <h2>Bean Validation contract</h2>
 *
 * <p>The {@link NotBlank @NotBlank}, {@link Size @Size}, and {@link Email @Email}
 * annotations from Jakarta Bean Validation 3.1 enforce input integrity both at
 * controller request-binding time (when an inbound DTO is mapped to this entity by
 * {@code AuthService.register}) and as a defense-in-depth check at persistence
 * time. The size limits intentionally mirror the {@code @Column(length = ...)}
 * declarations so that constraint violations surface as readable validation
 * errors rather than as opaque database errors.
 *
 * <h2>Lombok contract</h2>
 *
 * <p>The combination of {@link Data @Data} and {@link NoArgsConstructor
 * @NoArgsConstructor} generates, at compile time, the public no-argument
 * constructor required by the JPA specification (used by Hibernate when
 * materializing rows into managed instances), the standard accessor and mutator
 * pair for each field, and the canonical {@code equals}, {@code hashCode}, and
 * {@code toString} implementations consistent with the existing {@code Product}
 * entity. The {@link ToString.Exclude @ToString.Exclude} annotation on
 * {@link #password} is critical for security: without it, the Lombok-generated
 * {@code toString} would include the BCrypt hash, and any DEBUG-level logging
 * that incidentally serialized a {@code User} instance would expose credentials
 * to log aggregation systems. The hand-written three-argument constructor
 * {@link #User(String, String, String)} coexists with the Lombok-generated
 * no-arg constructor and provides the single-statement instantiation pattern
 * (e.g., {@code new User(username, email, encodedPassword)}) used by
 * {@code AuthService.register}.
 *
 * <h2>Spring Security relationship</h2>
 *
 * <p>This entity is the persistence-layer counterpart to Spring Security's
 * {@code UserDetails} contract. The adapter chain is:
 *
 * <pre>
 *   User (JPA entity)
 *     |  loadUserByUsername(username)
 *     v
 *   UserDetailsServiceImpl
 *     |  UserDetailsImpl.build(user)
 *     v
 *   UserDetailsImpl (implements UserDetails)
 *     |  authentication.getPrincipal()
 *     v
 *   SecurityContextHolder
 * </pre>
 *
 * <p>The {@code User.password} field supplies the BCrypt hash that
 * {@code DaoAuthenticationProvider} verifies via
 * {@code BCryptPasswordEncoder.matches(rawPassword, encodedPassword)} during the
 * {@code POST /api/auth/login} flow. The {@code roles} relationship supplies the
 * authority strings that gate the {@code @PreAuthorize("hasRole('ADMIN')")} and
 * {@code @PreAuthorize("hasAnyRole('USER','ADMIN')")} expressions on
 * {@code ProductController} and {@code StudentController} methods.
 *
 * @see Role
 * @see ERole
 * @see com.jspider.spring_boot_simple_crud_with_mysql.repository.UserRepository
 */
@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "username"),
           @UniqueConstraint(columnNames = "email")
       })
@Data
@NoArgsConstructor
public class User {

    /**
     * Surrogate primary key for the user row, auto-generated by the database.
     * Boxed as {@link Long} (rather than the primitive {@code long}) so that an
     * unsaved, transient {@code User} instance can carry a {@code null} id; the
     * persistence provider then assigns a real value on the first {@code INSERT}.
     * The type is deliberately {@link Long} rather than {@link Integer} because
     * the user population is unbounded and may eventually exceed the 32-bit
     * range, and because the companion {@code UserRepository} declares its key
     * type as {@code JpaRepository<User, Long>}.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user-chosen login identifier, stored as the canonical lookup key for
     * authentication. Constrained to a maximum of 20 characters at both the
     * Bean Validation layer (via {@link Size @Size}) and the database column
     * level (via {@link Column @Column}); the matching length declarations
     * ensure that an over-length value surfaces as a readable validation error
     * rather than a database truncation. The {@code NOT NULL UNIQUE} column
     * constraints are mandatory: {@code findByUsername} is the lookup method
     * invoked by {@code UserDetailsServiceImpl.loadUserByUsername} on every
     * authenticated request, and {@code existsByUsername} is the duplicate-check
     * gate in {@code AuthService.register}.
     */
    @NotBlank
    @Size(max = 20)
    @Column(nullable = false, length = 20)
    private String username;

    /**
     * The user's contact email address, validated for both presence and format
     * by Jakarta Bean Validation. The {@link Email @Email} constraint enforces
     * a syntactically valid address (it does not verify deliverability). The
     * {@code NOT NULL UNIQUE} column constraints support
     * {@code existsByEmail} duplicate-prevention in {@code AuthService.register}
     * and preserve the one-account-per-email invariant.
     */
    @NotBlank
    @Size(max = 50)
    @Email
    @Column(nullable = false, length = 50)
    private String email;

    /**
     * The user's password stored as a BCrypt hash. The plain-text form supplied
     * at registration time is converted by {@code BCryptPasswordEncoder.encode}
     * inside {@code AuthService.register} before the entity is persisted; the
     * raw password never reaches this column. The 120-character column ceiling
     * comfortably exceeds BCrypt's 60-character output and leaves headroom for
     * future encoder upgrades.
     *
     * <p>The {@link ToString.Exclude @ToString.Exclude} annotation is a security
     * non-negotiable: Lombok's {@code @Data}-generated {@code toString} would
     * otherwise include the BCrypt hash, and any DEBUG-level logging that
     * serialized a {@code User} instance (for example, Spring's
     * request-binding diagnostic logging) would leak the credential into
     * downstream log-aggregation systems. Excluding the field from
     * {@code toString} closes that exposure path.
     */
    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    @ToString.Exclude
    private String password;

    /**
     * The set of {@link Role roles} granted to this user, materialized through
     * the {@code user_roles} many-to-many join table. The relationship is
     * {@link FetchType#EAGER EAGER}-loaded so that the role set is materialized
     * on the same {@code SELECT} that produces the user, which is a correctness
     * requirement for {@code UserDetailsImpl.build(User)}: that adapter is
     * invoked by {@code UserDetailsServiceImpl.loadUserByUsername} during
     * authentication and may be invoked outside an open transaction, which
     * would cause LAZY-loaded collections to throw
     * {@code LazyInitializationException}.
     *
     * <p>The field is initialized to an empty {@link HashSet} so that
     * {@code AuthService.register} (and any other caller) can safely invoke
     * {@link #getRoles()}{@code .add(...)} on a freshly constructed entity
     * without a {@code NullPointerException}. The {@link Set} contract is
     * appropriate because role assignments are unordered and unique; a user
     * cannot hold the same role twice.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
               joinColumns = @JoinColumn(name = "user_id"),
               inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /**
     * Convenience constructor for the registration workflow. Used by
     * {@code AuthService.register} to build a fresh {@code User} instance from
     * the validated {@code SignupRequest} payload prior to assigning roles and
     * persisting through {@code UserRepository.save}.
     *
     * <p>The {@code id} field deliberately remains {@code null} after
     * construction; Hibernate populates it on {@code save} via the
     * {@code IDENTITY} generation strategy declared on {@link #id}. The
     * {@code roles} field retains its empty {@link HashSet} initializer so
     * that the caller can populate the role set on the constructed instance
     * before persisting it.
     *
     * @param username the unique login identifier; must satisfy the
     *                 {@code @NotBlank @Size(max = 20)} constraints when the
     *                 entity is subsequently validated or persisted.
     * @param email    the unique contact email; must satisfy the
     *                 {@code @NotBlank @Size(max = 50) @Email} constraints when
     *                 the entity is subsequently validated or persisted.
     * @param password the BCrypt-hashed password (never the plain-text form);
     *                 must satisfy the {@code @NotBlank @Size(max = 120)}
     *                 constraints when the entity is subsequently validated or
     *                 persisted.
     */
    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}
