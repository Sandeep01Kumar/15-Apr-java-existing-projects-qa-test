package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jspider.spring_boot_simple_crud_with_mysql.dao.ProductDao;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.Product;
import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/product")
@CrossOrigin(value = "")
@Tag(name = "productcontroller", description = "this is controller class")
public class ProductController {

	@Autowired
	ProductDao productDao;

	@Autowired
	ResponseStructure<Product> responseStructure;

	@GetMapping(value = "/getTodayDate")
	public String getTodaysDate() {

		return LocalDate.now() + " ";
	}

	@PostMapping(value = "/saveProduct")
	@Operation(description = "it will save one object at a time",
	responses = {
			@ApiResponse(responseCode = "200", description = "Product saved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid input, object not saved"),
			@ApiResponse(responseCode = "406", description = "Not acceptable, validation failed"),
			@ApiResponse(responseCode = "500", description = "Internal server error") }

	)
	public ResponseStructure<Product> saveProductController(@RequestBody Product product) {

		System.out.println(product);

		Product product2 = productDao.saveProductDao(product);

		if (product2 != null) {
			responseStructure.setStatusCode(HttpStatus.OK.value());
			responseStructure.setApiDescription("save product Secessfully...");
			responseStructure.setData(product2);
			return responseStructure;
		} else {

			responseStructure.setStatusCode(HttpStatus.NOT_ACCEPTABLE.value());
			responseStructure.setApiDescription("data not saved something went wrong");
			responseStructure.setData(product2);
			return responseStructure;
		}

	}

	@PostMapping(value = "/saveProducts")
	public List<Product> saveProductController(@RequestBody List<Product> products) {

		System.out.println(products);
		return productDao.saveMultipleProductDao(products);
	}

	@GetMapping(value = "/findAllProduct")
	public List<Product> findAllProductController() {

		return productDao.displayAllProductDao();
	}

	@GetMapping(value = "/getProduct/{id}")
	public Product getProductByIdController(@PathVariable(name = "id") Integer id) {

		return productDao.getProductByIdDao(id);
	}

	@GetMapping(value = "/getProductByName/{name}")
	public List<Product> getProductByNameDao(@PathVariable(name = "name") String name) {
		return productDao.getProductByNameDao(name);
	}

	@GetMapping(value = "/getProductByPrice/{price}")
	public List<Product> getProductByPriceController(@PathVariable(name = "price") double price) {
		return productDao.getProductByPriceDao(price);
	}

	@DeleteMapping(value = "/deleteProductByPrice/{price}")
	public void deleteProductByPriceController(@PathVariable(name = "price") double price) {

		productDao.deleteProductByPriceDao(price);
	}
	
	//update
	
	

	@PutMapping(value = "/updateProduct/{id}")
	@Operation(description = "it will update one object at a time",
	responses = {
			@ApiResponse(responseCode = "200", description = "Product Update successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid input, object not saved"),
			@ApiResponse(responseCode = "406", description = "Not acceptable, validation failed"),
			@ApiResponse(responseCode = "500", description = "Internal server error") }

	)
	public ResponseStructure<Product> updateProductController(@RequestBody Product userproduct, @PathVariable(name = "id") Integer id) {
        
		 

		Product product2 = productDao.updateProductDao(userproduct, id);

		if (product2 != null) {
			responseStructure.setStatusCode(HttpStatus.OK.value());
			responseStructure.setApiDescription("update product Secessfully...");
			responseStructure.setData(product2);
			return responseStructure;
		} else {

			responseStructure.setStatusCode(HttpStatus.NOT_ACCEPTABLE.value());
			responseStructure.setApiDescription("data not saved something went wrong");
			responseStructure.setData(product2);
			return responseStructure;
		}

	}

	
	@PutMapping("/{id}")
	public ResponseEntity<Product> updateProduct(@RequestBody Product product, @PathVariable Integer id) {
	    try {
	        Product updatedProduct = productDao.updateProductDao(product, id);
	        return new ResponseEntity<Product>(updatedProduct, HttpStatus.OK);
	    } catch (RuntimeException e) {
	        return new ResponseEntity<Product>(HttpStatus.NOT_FOUND); // Make sure this line ends with ;
	    }
	}

	
	
	
 
	
}
