package com.jspider.spring_boot_simple_crud_with_mysql.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.ERole;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.Role;

/**
 * Spring Data JPA repository for the {@link Role} entity.
 *
 * <p>This interface is the persistence-layer gateway through which the
 * application's authentication and authorization workflow resolves named
 * security roles. Spring Data generates a runtime proxy implementation of this
 * interface at application startup and registers it as a Spring bean named
 * {@code roleRepository}; collaborators consume the bean exclusively through
 * constructor injection (or {@code @Autowired} on legacy components) and never
 * instantiate it directly.
 *
 * <h2>Typed contract</h2>
 *
 * <p>The repository declares its identifier type as {@link Integer} (rather than
 * {@link Long}, which is the choice for the analogous {@code UserRepository}).
 * The asymmetry is deliberate and reflects the bounded, finite nature of the
 * role catalog: only the two values defined by {@link ERole}
 * &mdash; {@link ERole#ROLE_USER} and {@link ERole#ROLE_ADMIN} &mdash; are
 * persisted, and a 32-bit surrogate key is comfortably sufficient. The type
 * parameter <strong>must</strong> match the {@code Role.id} field declaration
 * exactly; a mismatch (for instance, declaring
 * {@code JpaRepository<Role, Long>} here while {@code Role.id} remains
 * {@code Integer}) would cause Spring Data to abort context initialization with
 * an {@code IllegalArgumentException} citing an "Identifier wrong type" or
 * "incompatible type" condition during proxy generation.
 *
 * <h2>Inherited operations</h2>
 *
 * <p>By extending {@link JpaRepository}, this repository inherits the full
 * Spring Data JPA CRUD surface: {@code save}, {@code saveAll},
 * {@code findById}, {@code findAll}, {@code count}, {@code existsById},
 * {@code deleteById}, {@code delete}, {@code deleteAll}, plus pagination and
 * sorting variants. Among those inherited methods, two are exercised directly
 * by the JWT authentication feature:
 *
 * <ul>
 *   <li>{@code count()} is invoked by the {@code roleSeeder}
 *       {@code ApplicationRunner} bean defined on the application's bootstrap
 *       class to perform an idempotent existence check; if {@code count()}
 *       returns zero on application startup, the seeder persists one fresh
 *       {@link Role} row for each {@link ERole} constant. Subsequent restarts
 *       see a non-zero count and skip the seeding step, which preserves any
 *       additional administrator-managed role state.</li>
 *   <li>{@code save(Role)} is invoked by the same seeder to write the seed
 *       rows. It is also implicitly invoked by Hibernate cascade operations
 *       triggered when {@code UserRepository.save(User)} writes a new user
 *       holding references to {@code Role} instances retrieved through
 *       {@link #findByName(ERole)} below.</li>
 * </ul>
 *
 * <h2>Derived query method</h2>
 *
 * <p>This repository declares exactly one custom query: the derived finder
 * {@link #findByName(ERole)}. Spring Data's method-name parser interprets the
 * {@code findBy} prefix together with the {@code Name} property name to
 * generate the JPQL query {@code SELECT r FROM Role r WHERE r.name = :name} at
 * runtime; no {@code @Query} annotation is necessary and none is permitted in
 * this declaration.
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
 * companion {@code UserRepository}.
 *
 * @see Role
 * @see ERole
 * @see com.jspider.spring_boot_simple_crud_with_mysql.repository.UserRepository
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    /**
     * Locates the single {@link Role} row whose {@code name} column equals the
     * supplied {@link ERole} constant.
     *
     * <p>Spring Data JPA materializes this method into the JPQL query
     * {@code SELECT r FROM Role r WHERE r.name = :name} at proxy-generation
     * time; the parameter is bound positionally and translated to the
     * canonical {@code VARCHAR} representation of the enum (for example,
     * {@code "ROLE_USER"}) by Hibernate's
     * {@code @Enumerated(EnumType.STRING)} converter declared on the
     * {@code Role.name} field. The accepting parameter type is therefore the
     * type-safe {@link ERole} enum rather than a raw {@link String}: callers
     * that mistype a constant name (for example, {@code "ROLE_USR"}) are
     * caught at compile time rather than at runtime.
     *
     * <h3>Result semantics</h3>
     *
     * <p>The return type is {@link Optional} of {@link Role} so that the two
     * possible outcomes &mdash; "role row present" and "role row absent"
     * &mdash; are surfaced uniformly through the {@code Optional} API:
     *
     * <ul>
     *   <li>If a row exists with the supplied name, the returned
     *       {@code Optional} wraps the managed {@code Role} entity. Callers
     *       typically dereference it through
     *       {@code optional.orElseThrow(...)} when the role is required for
     *       the operation to proceed; for example,
     *       {@code AuthService.register} resolves the requested role and
     *       attaches it to the freshly constructed {@code User} prior to
     *       calling {@code userRepository.save}.</li>
     *   <li>If no row exists with the supplied name, the returned
     *       {@code Optional} is empty. This condition signals that the role
     *       seeder has not yet executed (or, in misconfigured deployments,
     *       has been disabled); callers should treat this as an unrecoverable
     *       startup-data condition rather than as a normal business outcome,
     *       because the {@link ERole} enum is the closed set of possible
     *       inputs and every constant is expected to map to exactly one row.</li>
     * </ul>
     *
     * <h3>Database interaction</h3>
     *
     * <p>At the SQL layer the query reduces to {@code SELECT id, name FROM
     * roles WHERE name = ?} with the bound parameter rendered as a string
     * literal corresponding to the enum's {@code name()} value. The
     * {@code roles.name} column is short ({@code VARCHAR(20)}) and the table
     * is small (currently two rows), so the query executes in microseconds
     * even without an explicit index. No locking semantics are applied;
     * concurrent invocations are safe.
     *
     * @param name the {@link ERole} constant to match against the
     *             {@code roles.name} column; must not be {@code null} (a null
     *             argument would result in a JPQL {@code IS NULL} comparison
     *             that does not match any row stored under
     *             {@code @Enumerated(EnumType.STRING)} semantics)
     * @return an {@code Optional} carrying the matching {@code Role} entity if
     *         one exists in the database, or an empty {@code Optional} if no
     *         row matches the supplied name
     */
    Optional<Role> findByName(ERole name);
}
