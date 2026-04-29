package com.jspider.spring_boot_simple_crud_with_mysql;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Application context smoke test.
 *
 * <p>The production {@code application.properties} configures a MySQL datasource
 * (see {@code src/main/resources/application.properties}). For the smoke test,
 * we override the four {@code spring.datasource.*} properties, the Hibernate
 * dialect, and the JWT signing key so the test can boot the full Spring context
 * without requiring a live MySQL instance. The H2 in-memory database is on the
 * runtime classpath via the existing {@code com.h2database:h2} dependency in
 * {@code pom.xml}, and Hibernate auto-creates the {@code product},
 * {@code users}, {@code roles}, and {@code user_roles} tables for the duration
 * of the test JVM.
 *
 * <p>The test JWT secret is a Base64-encoded value that decodes to more than
 * 32 bytes (256 bits), satisfying the RFC 7518 §3.2 minimum HS256 key length
 * required by {@code JwtUtils}. The 1-hour expiration is intentionally shorter
 * than the production 24-hour default to keep test tokens short-lived.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=LEGACY",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
		"jwt.secret=dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItdW5pdC10ZXN0aW5nLW9ubHktbm90LWZvci1wcm9kdWN0aW9uLXVzZQ==",
		"jwt.expiration=3600000"
})
class SpringBootSimpleCrudWithMysqlApplicationTests {

	@Test
	void contextLoads() {
	}

}
