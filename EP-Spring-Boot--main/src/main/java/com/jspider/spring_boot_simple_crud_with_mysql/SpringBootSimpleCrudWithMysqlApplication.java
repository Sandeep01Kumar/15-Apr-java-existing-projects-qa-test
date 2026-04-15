package com.jspider.spring_boot_simple_crud_with_mysql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(
		info = @Info(
				title = "Product-Crud-Operation",
				description = "we perform crud operartion with mysql db",
				version = "1.0.0",
				contact = @Contact(
						name = "Sudhir Yadav ",
						email = "qasudhir@gmail.com",
						url = "https://www.w3schools.com/"
						)
				)
		)
public class SpringBootSimpleCrudWithMysqlApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringBootSimpleCrudWithMysqlApplication.class, args);
		
		System.out.println("All Right Sudhir...........");
	}

}
