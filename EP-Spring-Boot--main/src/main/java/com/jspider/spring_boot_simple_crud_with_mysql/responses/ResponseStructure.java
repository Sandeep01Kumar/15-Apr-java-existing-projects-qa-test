package com.jspider.spring_boot_simple_crud_with_mysql.responses;

import org.springframework.stereotype.Component;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Component
@Schema(hidden = true)
public class ResponseStructure<T> {

	private int statusCode;
	private String apiDescription;
	private T data;
}
