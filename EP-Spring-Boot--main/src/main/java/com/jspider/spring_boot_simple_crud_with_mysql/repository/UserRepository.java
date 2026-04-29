package com.jspider.spring_boot_simple_crud_with_mysql.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;

/**
 * Spring Data JPA repository for the {@link User} entity.
 *
 * <p>This interface is the persistence-layer gateway through which the JWT
 * authentication and authorization workflow resolves user identities. Spring
 * Data generates a runtime proxy implementation of this interface at
 * application startup and registers it as a Spring bean named
 * {@code userRepository}; collaborators consume the bean exclusively through
 * dependency injection (constructor injection on new code such as
 * {@code AuthService}, field injection through {@code @Autowired} on the
 * legacy-style {@code UserDetailsServiceImpl}) and never instantiate it
 * directly.
 *
 * <h2>Typed contract</h2>
 *
 * <p>The repository declares its identifier type as {@link Long} (rather than
 * {@link Integer}, which is the choice for the companion {@code RoleRepository}
 * and the legacy {@code ProductRepository}). The asymmetry is deliberate: the
 * {@link User} entity's surrogate primary key is declared as {@code Long} on
 * the assumption that the user population grows unboundedly, while the
 * {@code Role} catalog is bounded to the two entries defined by
 * {@code ERole}. The type parameter <strong>must</strong> match the
 * {@code User.id} field declaration exactly; a mismatch (for instance,
 * declaring {@code JpaRepository<User, Integer>} here while {@code User.id}
 * remains {@code Long}) would cause Spring Data to abort context
 * initialization with an {@code IllegalArgumentException} citing an
 * "Identifier wrong type" or "incompatible type" condition during proxy
 * generation.
 *
 * <h2>Inherited operations</h2>
 *
 * <p>By extending {@link JpaRepository}, this repository inherits the full
 * Spring Data JPA CRUD surface: {@code save}, {@code saveAll},
 * {@code findById}, {@code findAll}, {@code findAllById}, {@code count},
 * {@code existsById}, {@code deleteById}, {@code delete}, {@code deleteAll},
 * {@code deleteAllById}, {@code flush}, {@code saveAndFlush}, plus pagination
 * and sorting variants. Among those inherited methods, {@code save(User)} is
 * exercised directly by {@code AuthService.register} to persist a freshly
 * constructed user with its associated roles into the {@code users} and
 * {@code user_roles} tables; the cascade behavior of the
 * {@code User.roles} {@code @ManyToMany} association ensures that the
 * {@code user_roles} join rows are written in the same transaction.
 *
 * <h2>Derived query methods</h2>
 *
 * <p>This repository declares three custom queries, all expressed as
 * Spring Data derived finders. Spring Data's method-name parser inspects each
 * method signature at proxy-generation time and produces the corresponding
 * JPQL implementation; no {@code @Query} annotation is necessary, and none is
 * permitted because the parser already resolves the property names against
 * the {@link User} entity's field declarations.
 *
 * <ul>
 *   <li>{@link #findByUsername(String)} backs the authentication path
 *       beginning at {@code UserDetailsServiceImpl.loadUserByUsername}; it is
 *       invoked once per protected request to materialize the principal that
 *       Spring Security exposes through the {@code SecurityContextHolder}.
 *       The {@code users.username} column carries a unique constraint and a
 *       database-level index per the {@link User} entity's
 *       {@code @UniqueConstraint(columnNames = "username")} declaration, so
 *       this lookup runs in {@code O(log n)} time even under heavy
 *       authentication load.</li>
 *   <li>{@link #existsByUsername(String)} backs the duplicate-prevention gate
 *       in {@code AuthService.register}; it returns {@code Boolean.TRUE} when
 *       a row already exists with the supplied username and
 *       {@code Boolean.FALSE} otherwise, allowing the service layer to throw
 *       a {@code IllegalArgumentException} that the
 *       {@code GlobalExceptionHandler} translates into an HTTP 400 response.</li>
 *   <li>{@link #existsByEmail(String)} backs the second duplicate-prevention
 *       gate in the same registration flow, enforcing the
 *       one-account-per-email invariant that mirrors the
 *       {@code @UniqueConstraint(columnNames = "email")} declared on the
 *       {@link User} entity.</li>
 * </ul>
 *
 * <h2>Stereotype annotation</h2>
 *
 * <p>The interface is annotated with {@link Repository @Repository} for two
 * reasons: first, it documents the bean's role in the application architecture
 * for static-analysis tools and for human readers; second, it activates
 * Spring's {@code PersistenceExceptionTranslationPostProcessor} so that
 * provider-specific {@code jakarta.persistence.PersistenceException} instances
 * thrown beneath this layer are transparently rewrapped as Spring's
 * {@code DataAccessException} hierarchy, allowing service-layer code such as
 * {@code AuthService.register} to catch a uniform exception family. Although
 * the {@code @Repository} annotation is technically redundant for any
 * {@link JpaRepository} extension (Spring Data registers all such interfaces
 * as repository beans automatically), keeping it on this declaration aligns
 * the new authentication-feature code with the pattern adopted by the
 * companion {@code RoleRepository} and reflects the AAP §0.7.1.2 directive of
 * "apply best practices to NEW code" while preserving existing-code
 * conventions on {@code ProductRepository}.
 *
 * @see User
 * @see com.jspider.spring_boot_simple_crud_with_mysql.repository.RoleRepository
 * @see org.springframework.data.jpa.repository.JpaRepository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Locates the single {@link User} row whose {@code username} column equals
     * the supplied value.
     *
     * <p>Spring Data JPA materializes this method into the JPQL query
     * {@code SELECT u FROM User u WHERE u.username = :username} at
     * proxy-generation time. The underlying SQL reduces to
     * {@code SELECT id, username, email, password FROM users WHERE username = ?}
     * (with the {@code user_roles} join eagerly issued separately because of
     * the {@code @ManyToMany(fetch = FetchType.EAGER)} declaration on
     * {@code User.roles}), and the {@code username} column carries a unique
     * index established by {@code User.@UniqueConstraint(columnNames =
     * "username")} so the lookup is performance-critical-safe even when
     * invoked on every authenticated request.
     *
     * <h3>Result semantics</h3>
     *
     * <p>The return type is {@link Optional} of {@link User} so that the two
     * possible outcomes &mdash; "user row present" and "user row absent"
     * &mdash; are surfaced uniformly through the {@code Optional} API:
     *
     * <ul>
     *   <li>If a row exists with the supplied username, the returned
     *       {@code Optional} wraps the managed {@code User} entity (with its
     *       {@code roles} association eagerly initialized). Callers
     *       typically dereference it through
     *       {@code optional.orElseThrow(new UsernameNotFoundException(...))}
     *       in {@code UserDetailsServiceImpl.loadUserByUsername} so the
     *       Spring Security authentication chain can proceed.</li>
     *   <li>If no row exists with the supplied username, the returned
     *       {@code Optional} is empty. This condition is the canonical
     *       "user not found" signal that
     *       {@code UserDetailsServiceImpl.loadUserByUsername} translates into
     *       a {@code UsernameNotFoundException}, which the
     *       {@code GlobalExceptionHandler} subsequently surfaces as an
     *       HTTP 401 or HTTP 404 response depending on the call site.</li>
     * </ul>
     *
     * <h3>Concurrency and locking</h3>
     *
     * <p>No locking semantics are applied; concurrent invocations are safe
     * and idempotent. Read-only access against the {@code users} table does
     * not require an explicit transaction boundary, although Spring Data
     * automatically applies a non-locking transactional context when the
     * caller has not declared one.
     *
     * @param username the {@code users.username} column value to match;
     *                 expected to be non-{@code null} and non-blank per the
     *                 {@code @NotBlank} constraint on {@code User.username},
     *                 although the JPQL {@code = :username} comparison
     *                 against a {@code null} parameter would simply yield an
     *                 empty result rather than raising an exception
     * @return an {@code Optional} carrying the matching {@code User} entity
     *         if one exists in the database, or an empty {@code Optional} if
     *         no row matches the supplied username
     */
    Optional<User> findByUsername(String username);

    /**
     * Reports whether a {@link User} row exists with the supplied
     * {@code username} value, without materializing the row itself.
     *
     * <p>Spring Data JPA materializes this method into a count-style JPQL
     * query of the form
     * {@code SELECT COUNT(u) FROM User u WHERE u.username = :username}
     * (the framework optimizes the comparison to {@code > 0} internally) at
     * proxy-generation time. The corresponding SQL reduces to
     * {@code SELECT COUNT(*) FROM users WHERE username = ?} and exits early
     * on the first matching row because of the unique index established by
     * {@code User.@UniqueConstraint(columnNames = "username")}.
     *
     * <p>This method is the duplicate-prevention gate invoked by
     * {@code AuthService.register} before attempting to persist a freshly
     * constructed {@code User}: a {@code TRUE} return value causes the
     * service to throw an {@code IllegalArgumentException} carrying the
     * message {@code "Error: Username is already taken!"}, which the
     * {@code GlobalExceptionHandler} subsequently surfaces as an HTTP 400
     * response with the same message in the body.
     *
     * <p>The return type is the boxed {@link Boolean} (rather than the
     * primitive {@code boolean}) per the AAP §0.5.1.2 contract, which
     * preserves the option of a {@code null} return value at the language
     * level even though Spring Data always returns either
     * {@link Boolean#TRUE} or {@link Boolean#FALSE} from this query
     * shape.
     *
     * @param username the {@code users.username} column value to test for
     *                 existence; expected to be non-{@code null} and
     *                 non-blank per the {@code @NotBlank} constraint on
     *                 {@code User.username}
     * @return {@link Boolean#TRUE} if at least one row exists with the
     *         supplied username, {@link Boolean#FALSE} otherwise
     */
    Boolean existsByUsername(String username);

    /**
     * Reports whether a {@link User} row exists with the supplied
     * {@code email} value, without materializing the row itself.
     *
     * <p>Spring Data JPA materializes this method into a count-style JPQL
     * query of the form {@code SELECT COUNT(u) FROM User u WHERE u.email
     * = :email} (the framework optimizes the comparison to {@code > 0}
     * internally) at proxy-generation time. The corresponding SQL reduces
     * to {@code SELECT COUNT(*) FROM users WHERE email = ?} and exits early
     * on the first matching row because of the unique index established by
     * {@code User.@UniqueConstraint(columnNames = "email")}.
     *
     * <p>This method is the second duplicate-prevention gate invoked by
     * {@code AuthService.register} (after {@link #existsByUsername(String)}):
     * a {@code TRUE} return value causes the service to throw an
     * {@code IllegalArgumentException} carrying the message
     * {@code "Error: Email is already in use!"}, which the
     * {@code GlobalExceptionHandler} subsequently surfaces as an HTTP 400
     * response. The check enforces the one-account-per-email invariant
     * baked into the {@code @UniqueConstraint(columnNames = "email")}
     * declaration on the {@link User} entity, surfacing the violation as a
     * readable validation error rather than letting it fall through to a
     * raw database constraint violation.
     *
     * <p>The return type is the boxed {@link Boolean} (rather than the
     * primitive {@code boolean}) per the AAP §0.5.1.2 contract, which
     * preserves the option of a {@code null} return value at the language
     * level even though Spring Data always returns either
     * {@link Boolean#TRUE} or {@link Boolean#FALSE} from this query
     * shape.
     *
     * @param email the {@code users.email} column value to test for
     *              existence; expected to be non-{@code null}, non-blank,
     *              and well-formed per the
     *              {@code @NotBlank @Email} constraints on
     *              {@code User.email}
     * @return {@link Boolean#TRUE} if at least one row exists with the
     *         supplied email, {@link Boolean#FALSE} otherwise
     */
    Boolean existsByEmail(String email);
}
