package com.jspider.spring_boot_simple_crud_with_mysql.entity;

/**
 * Security role enumeration for the JWT authentication and authorization layer.
 *
 * <p>This enum defines the two named roles supported by the application's role-based
 * access-control (RBAC) model. Instances of this enum are persisted (via
 * {@code jakarta.persistence.EnumType.STRING}) on the {@code roles.name} column managed
 * by the {@code Role} entity, looked up at registration time by
 * {@code RoleRepository.findByName(ERole)}, and surfaced as
 * {@code SimpleGrantedAuthority} strings on Spring Security's {@code Authentication}
 * principal during request processing.
 *
 * <h2>The {@code ROLE_} prefix is mandatory, by framework convention</h2>
 *
 * <p>Spring Security's {@code hasRole(...)} and {@code hasAnyRole(...)} SpEL
 * expressions <em>internally prepend</em> the literal string {@code "ROLE_"} to each
 * argument before comparing against the authorities held by the current authenticated
 * principal. As a direct consequence:
 *
 * <ul>
 *   <li>{@code @PreAuthorize("hasRole('USER')")} matches a granted authority whose
 *       string representation is exactly {@code "ROLE_USER"}.</li>
 *   <li>{@code @PreAuthorize("hasRole('ADMIN')")} matches a granted authority whose
 *       string representation is exactly {@code "ROLE_ADMIN"}.</li>
 *   <li>{@code @PreAuthorize("hasAnyRole('USER', 'ADMIN')")} matches a principal that
 *       holds either {@code "ROLE_USER"} or {@code "ROLE_ADMIN"}.</li>
 * </ul>
 *
 * <p>Therefore the only correct combination is to <strong>store</strong> the enum
 * constants with the {@code ROLE_} prefix (as defined here) and to <strong>reference</strong>
 * them in SpEL expressions <em>without</em> the prefix (e.g., {@code hasRole('USER')},
 * not {@code hasRole('ROLE_USER')}). Storing them without the prefix or referencing
 * them with the prefix would cause the framework's authority comparison to fail
 * silently &mdash; resulting in unexpected HTTP 403 responses on every protected
 * endpoint.
 *
 * <h2>Persistence semantics</h2>
 *
 * <p>The companion {@code Role} entity declares its {@code name} field with
 * {@code @Enumerated(EnumType.STRING)}, which means the canonical string forms
 * {@code "ROLE_USER"} and {@code "ROLE_ADMIN"} (rather than ordinal integers
 * {@code 0} and {@code 1}) are persisted to the {@code roles.name} column. This
 * persistence strategy is robust to future reordering of the constants in this enum.
 *
 * <h2>Scope</h2>
 *
 * <p>Only two roles are supported in this iteration of the authentication feature, as
 * specified by the project's authentication-feature plan. Adding additional roles
 * (e.g., {@code ROLE_GUEST}, {@code ROLE_MODERATOR}) is intentionally out of scope and
 * would require a separate change request that updates the seeder, the registration
 * service, the integration tests, and the API documentation.
 *
 * @see com.jspider.spring_boot_simple_crud_with_mysql.entity.Role
 */
public enum ERole {

    /**
     * Standard authenticated-user role. Granted by default to every newly registered
     * account when the {@code SignupRequest.role} field is null or empty. Authorizes
     * read-style operations on the existing CRUD endpoints (e.g.,
     * {@code GET /product/findAllProduct}, {@code GET /product/{id}}) under the
     * {@code @PreAuthorize("hasAnyRole('USER', 'ADMIN')")} expression.
     */
    ROLE_USER,

    /**
     * Privileged administrator role. Granted only when explicitly requested via the
     * {@code SignupRequest.role} field at registration time. Authorizes write-style
     * operations on the existing CRUD endpoints (e.g.,
     * {@code POST /product/saveProduct}, {@code PUT /product/updateProduct},
     * {@code DELETE /product/deleteProductByPrice/{price}}) under the
     * {@code @PreAuthorize("hasRole('ADMIN')")} expression.
     */
    ROLE_ADMIN
}
