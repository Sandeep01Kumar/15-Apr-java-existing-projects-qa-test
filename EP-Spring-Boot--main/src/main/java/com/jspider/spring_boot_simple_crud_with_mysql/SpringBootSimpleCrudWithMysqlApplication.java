package com.jspider.spring_boot_simple_crud_with_mysql;

import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.ERole;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.Role;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.RoleRepository;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@SpringBootApplication
@SecurityScheme(
		name = "bearerAuth",
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT"
		)
@OpenAPIDefinition(
		info = @Info(
				title = "Product-Crud-Operation",
				description = "we perform crud operartion with mysql db",
				version = "1.0.0",
				contact = @Contact(
						name = "",
						email = "",
						url = "https://www.w3schools.com/"
						)
				)
		)
public class SpringBootSimpleCrudWithMysqlApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootSimpleCrudWithMysqlApplication.class, args);
		
		System.out.println("All Right Sudhir...........");
	}

	/**
	 * Application-startup role seeder that idempotently populates the {@code roles}
	 * table with the two security roles required by the JWT authentication and
	 * authorization layer ({@link ERole#ROLE_USER} and {@link ERole#ROLE_ADMIN}).
	 *
	 * <p>Spring Boot invokes this {@link ApplicationRunner} after the application
	 * context has been fully refreshed and the embedded servlet container is
	 * accepting requests, which guarantees that the JPA {@code EntityManagerFactory}
	 * and the configured {@link javax.sql.DataSource} are ready for read/write
	 * traffic. Running the seeder this late in startup avoids the
	 * {@code LazyInitializationException} and partially-initialized-bean hazards
	 * that arise when the same logic is placed inside a {@code @PostConstruct} on
	 * a bean that loads before JPA infrastructure is available.
	 *
	 * <h3>Idempotency</h3>
	 *
	 * <p>The seeder consults {@link RoleRepository#count()} before issuing any
	 * insert. When the count is zero (the canonical first-startup state) the
	 * lambda persists exactly one fresh {@link Role} row per {@link ERole}
	 * constant via the convenience constructor {@code new Role(ERole)}. On every
	 * subsequent startup the count is non-zero and the lambda short-circuits
	 * without touching the database; this preserves any administrator-managed
	 * role state that may have been added (or deduplicated) out-of-band, and
	 * prevents duplicate-key violations against the surrogate primary key.
	 *
	 * <h3>Why this lives on the bootstrap class</h3>
	 *
	 * <p>Per the project's authentication-feature plan, the role seeder is a
	 * startup concern rather than a security-configuration concern. Co-locating
	 * it with {@code SpringApplication.run(...)} keeps {@code SecurityConfig}
	 * focused exclusively on filter-chain assembly, which is the canonical
	 * Spring Security 6.x idiom.
	 *
	 * @param roleRepository the Spring Data JPA repository for the {@link Role}
	 *                       entity, injected by Spring as the parameter to this
	 *                       {@code @Bean} factory method; never {@code null}
	 * @return an {@link ApplicationRunner} whose {@code run} method seeds the
	 *         {@code roles} table on first startup and is a no-op thereafter
	 */
	@Bean
	public ApplicationRunner roleSeeder(RoleRepository roleRepository) {
		return args -> {
			if (roleRepository.count() == 0) {
				roleRepository.save(new Role(ERole.ROLE_USER));
				roleRepository.save(new Role(ERole.ROLE_ADMIN));
				System.out.println("Seeded roles: ROLE_USER, ROLE_ADMIN");
			}
		};
	}

	/**
	 * Hardens the embedded Tomcat instance so that container-level error pages
	 * (rendered by Tomcat's {@link ErrorReportValve} when a request fails
	 * before reaching application code) do NOT leak the Tomcat version banner
	 * or any Java stack trace fragments to clients.
	 *
	 * <p><strong>Threat model</strong> &mdash; certain malformed requests
	 * (e.g. URLs containing characters disallowed by RFC 3986 such as
	 * {@code <}, {@code |}, raw {@code %00}; HTTP/1.1 protocol violations;
	 * truncated request lines) are rejected by Tomcat's HTTP/1.1 parser
	 * <em>before</em> Spring's {@code DispatcherServlet}, the Spring Security
	 * filter chain, or the application's {@code GlobalExceptionHandler} can
	 * see the request. In that fallback path Tomcat's stock
	 * {@link ErrorReportValve} renders an HTML error page whose default
	 * configuration includes:
	 * <ul>
	 *   <li>The Tomcat version banner ({@code "Apache Tomcat/X.Y.Z"}) from
	 *       {@code showServerInfo=true}, allowing CVE-targeting reconnaissance;</li>
	 *   <li>The Java stack trace of the failure from {@code showReport=true},
	 *       exposing internal package names, line numbers, and threading
	 *       internals that aid an attacker mapping the runtime.</li>
	 * </ul>
	 *
	 * <p><strong>Mitigation</strong> &mdash; this customizer iterates the
	 * Tomcat host pipeline and locates the existing {@link ErrorReportValve}
	 * instances (which Tomcat installs by default during {@code StandardHost}
	 * initialisation) and switches their {@code showReport} and
	 * {@code showServerInfo} flags to {@code false}. With both flags off the
	 * valve still emits an HTTP status line and a minimal status-code-only
	 * page body, satisfying conformance with the HTTP specification while
	 * suppressing all sensitive disclosure. If no {@link ErrorReportValve} is
	 * present on the pipeline (which can happen in non-default Tomcat
	 * topologies) the customizer is a no-op and registers a fresh suppressed
	 * valve so the protection still applies.
	 *
	 * <p><strong>Why this is at the application bootstrap class</strong>
	 * &mdash; the {@code @SpringBootApplication}-annotated class is the
	 * canonical home for {@link WebServerFactoryCustomizer} beans because the
	 * embedded server bootstrap is itself a Spring Boot top-level concern,
	 * not a security-configuration concern. Placing the customizer in a
	 * sibling configuration class (e.g. {@code SecurityConfig}) would mix
	 * unrelated lifecycle concerns and could shadow Spring Security's own
	 * filter-chain bean ordering. The bootstrap class also already exposes
	 * one application-wide {@code @Bean} method ({@link #roleSeeder}), so
	 * adding a second bean here preserves the project's "all bootstrap
	 * concerns in one file" convention without inflating the security
	 * package surface area.
	 *
	 * <p><strong>Defense-in-depth</strong> &mdash; this customizer is paired
	 * with the {@code server.error.include-message=never},
	 * {@code server.error.include-stacktrace=never},
	 * {@code server.error.include-exception=false},
	 * {@code server.error.include-binding-errors=never}, and
	 * {@code server.error.whitelabel.enabled=false} properties in
	 * {@code application.properties}. The valve customizer addresses the
	 * Tomcat-rendered error path; the {@code server.error.*} properties
	 * address the Spring-rendered {@code BasicErrorController} error path.
	 * Both paths are now uniformly hardened against information disclosure
	 * per AAP &sect;0.7.1.4.
	 *
	 * @return a {@link WebServerFactoryCustomizer} that mutates each
	 *         {@link ErrorReportValve} on the Tomcat host pipeline to
	 *         suppress server info and stack trace disclosure
	 */
	@Bean
	public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatErrorReportValveCustomizer() {
		return factory -> factory.addContextCustomizers(context -> {
			if (context.getParent() instanceof StandardHost host) {
				boolean foundExistingValve = false;
				// Mutate any existing ErrorReportValve on the host pipeline so its
				// default disclosure surface is suppressed before any error fires.
				for (Valve valve : host.getPipeline().getValves()) {
					if (valve instanceof ErrorReportValve errorReportValve) {
						errorReportValve.setShowReport(false);
						errorReportValve.setShowServerInfo(false);
						foundExistingValve = true;
					}
				}
				// Topology fallback: if Tomcat did not install a default
				// ErrorReportValve on this host, register a fresh one with the
				// hardened settings so error responses still get a minimal
				// non-disclosing body. Without this, container-level errors
				// could fall through to an even less-controlled default.
				if (!foundExistingValve) {
					ErrorReportValve hardened = new ErrorReportValve();
					hardened.setShowReport(false);
					hardened.setShowServerInfo(false);
					host.getPipeline().addValve(hardened);
				}
			}
		});
	}

}
