package com.jspider.spring_boot_simple_crud_with_mysql.security;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-Origin Resource Sharing (CORS) configuration for the Spring Boot
 * authentication and authorization layer.
 *
 * <p>This {@link Configuration} class exposes a single
 * {@link CorsConfigurationSource} bean that is auto-detected by Spring MVC
 * (and, when explicitly enabled via {@code http.cors(...)}, by Spring
 * Security) to apply a uniform Cross-Origin policy to every URL pattern
 * served by this application.</p>
 *
 * <p>The CORS posture is intentionally permissive in order to align with the
 * existing {@code @CrossOrigin(value = "")} annotation on
 * {@code ProductController} and to support local development against the new
 * {@code /api/auth/*} endpoints (registration, login). Production deployments
 * that need stricter origin controls should refine the allowed origin list
 * by overriding this bean or by replacing the wildcard with explicit hosts.
 * </p>
 *
 * <h2>Why this class exists</h2>
 * <p>Spring Security 6.x rejects the combination of
 * {@code setAllowedOrigins("*")} with {@code setAllowCredentials(true)} at
 * application startup with an {@link IllegalArgumentException}. By exposing
 * a dedicated {@code CorsConfigurationSource} bean with
 * {@code setAllowCredentials(false)}, this configuration permits wildcard
 * origins while satisfying Spring Security's startup validation.</p>
 *
 * <h2>Single Responsibility</h2>
 * <p>This class only configures CORS. Authentication and authorization
 * concerns live in the sibling {@code SecurityConfig} class. Splitting CORS
 * into its own configuration class keeps changes to either concern
 * independent and avoids cross-cutting coupling.</p>
 */
@Configuration
public class WebSecurityCorsConfig {

    /**
     * Builds the application-wide {@link CorsConfigurationSource} bean.
     *
     * <p>The configuration:</p>
     * <ul>
     *   <li>Allows any origin ({@code "*"}) - mirrors the existing
     *       {@code @CrossOrigin(value = "")} posture on
     *       {@code ProductController}.</li>
     *   <li>Permits the standard REST verbs plus {@code OPTIONS} so that
     *       browser preflight requests succeed.</li>
     *   <li>Permits any request header so callers may attach the
     *       {@code Authorization: Bearer &lt;jwt&gt;} header without further
     *       configuration.</li>
     *   <li>Disables credentials transmission - this is mandatory because
     *       the CORS specification (and Spring Security 6.x's startup
     *       validation) forbid {@code Access-Control-Allow-Credentials: true}
     *       in combination with {@code Access-Control-Allow-Origin: *}.</li>
     *   <li>Registers the policy under the {@code "/**"} URL pattern so the
     *       same rules apply to every controller-mapped path including
     *       {@code /api/auth/*}, {@code /product/*}, and {@code /student/*}.
     *       </li>
     * </ul>
     *
     * @return a fully-configured {@link UrlBasedCorsConfigurationSource}
     *         that Spring's request-handling infrastructure will consult on
     *         every request.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow any origin. The wildcard is acceptable here because
        // setAllowCredentials(false) below disables credential transmission.
        configuration.setAllowedOrigins(Arrays.asList("*"));
        // Permit the full REST verb set plus OPTIONS for preflight handling.
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Permit any header (notably Authorization for JWT-bearer requests).
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // CRITICAL: disable credentials so wildcard origins remain valid per
        // both the CORS specification and Spring Security 6.x validation.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        // Apply the same CORS policy to every path served by this application.
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
