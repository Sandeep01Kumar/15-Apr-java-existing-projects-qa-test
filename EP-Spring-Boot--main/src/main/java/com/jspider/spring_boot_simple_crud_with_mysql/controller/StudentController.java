package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping(value = "/student")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@GetMapping(value = "/getTodayDate")
	public String getTodaysDate() {
		
		return LocalDate.now()+" ";
	}
	
	@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
	@RequestMapping(value = "/addition/{a1}/{b1}",method = RequestMethod.POST)
	public int getAdditionOfTwoNumber(@PathVariable(name = "a1") int a,@PathVariable(name = "b1") int b) {
		
		return a+b;
	}
}
