package com.jspider.spring_boot_simple_crud_with_mysql;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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

}
