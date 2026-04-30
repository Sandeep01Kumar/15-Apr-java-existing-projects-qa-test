package com.jspider.spring_boot_simple_crud_with_mysql;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb",
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
