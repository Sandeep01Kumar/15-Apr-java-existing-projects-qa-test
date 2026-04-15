package com.jspider.spring_boot_simple_crud_with_mysql.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;


@Entity
@Data
@Schema(name = "product class",description = "this is product entity class")
public class Product {

	@Id
	private int id;
	private String name;
	private String color;
	@Schema(description = "price datatype is double")
	
	private double price;
	 
	
	 
}
