package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jspider.spring_boot_simple_crud_with_mysql.dao.ProductDao;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.Product;
import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;

/**
 * Unit tests for the product search-by-name endpoint in {@link ProductController}.
 *
 * <p>Uses Spring Boot's {@code @WebMvcTest} test slicing to load only the web layer
 * for {@code ProductController}. The DAO layer and ResponseStructure component are
 * mocked via {@code @MockitoBean} to isolate the controller from the persistence layer.</p>
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Successful search returning multiple matching products (HTTP 200)</li>
 *   <li>Search returning an empty result set (HTTP 200 with empty array)</li>
 *   <li>Missing required {@code name} query parameter (HTTP 400)</li>
 * </ul>
 */
@WebMvcTest(ProductController.class)
class ProductControllerSearchTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductDao productDao;

	@MockitoBean
	private ResponseStructure<Product> responseStructure;

	/**
	 * Verifies that {@code GET /product/search?name=laptop} returns HTTP 200 OK
	 * with a JSON array containing 2 matching products when the DAO returns results.
	 *
	 * <p>Validates:</p>
	 * <ul>
	 *   <li>HTTP status code is 200 OK</li>
	 *   <li>Response body is a JSON array with exactly 2 elements</li>
	 *   <li>All product fields (id, name, color, price) are correctly serialized</li>
	 *   <li>The DAO's {@code searchProductByNameDao} method is called exactly once
	 *       with the correct argument</li>
	 * </ul>
	 */
	@Test
	void testSearchByName_ReturnsMatchingProducts() throws Exception {
		// Arrange: Create 2 Product test objects with distinct field values
		Product product1 = new Product();
		product1.setId(1);
		product1.setName("Laptop Pro");
		product1.setColor("Silver");
		product1.setPrice(999.99);

		Product product2 = new Product();
		product2.setId(2);
		product2.setName("Gaming Laptop");
		product2.setColor("Black");
		product2.setPrice(1499.99);

		List<Product> products = Arrays.asList(product1, product2);

		// Mock the DAO method to return the test products
		when(productDao.searchProductByNameDao("laptop")).thenReturn(products);

		// Act & Assert: Perform GET request and verify response
		mockMvc.perform(get("/product/search")
						.param("name", "laptop")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id", is(1)))
				.andExpect(jsonPath("$[0].name", is("Laptop Pro")))
				.andExpect(jsonPath("$[0].color", is("Silver")))
				.andExpect(jsonPath("$[0].price", is(999.99)))
				.andExpect(jsonPath("$[1].id", is(2)))
				.andExpect(jsonPath("$[1].name", is("Gaming Laptop")))
				.andExpect(jsonPath("$[1].color", is("Black")))
				.andExpect(jsonPath("$[1].price", is(1499.99)));

		// Verify DAO was called exactly once with correct argument
		verify(productDao, times(1)).searchProductByNameDao("laptop");
	}

	/**
	 * Verifies that {@code GET /product/search?name=nonexistent} returns HTTP 200 OK
	 * with an empty JSON array when no products match the search term.
	 *
	 * <p>This confirms correct REST semantics: an empty search result returns 200 OK
	 * (not 404 Not Found), because the search operation succeeded — the result set
	 * is simply empty.</p>
	 */
	@Test
	void testSearchByName_ReturnsEmptyList() throws Exception {
		// Arrange: Mock DAO to return empty list for nonexistent product name
		when(productDao.searchProductByNameDao("nonexistent")).thenReturn(Collections.emptyList());

		// Act & Assert: Perform GET request and verify empty response
		mockMvc.perform(get("/product/search")
						.param("name", "nonexistent")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		// Verify DAO was called exactly once with correct argument
		verify(productDao, times(1)).searchProductByNameDao("nonexistent");
	}

	/**
	 * Verifies that {@code GET /product/search} without the required {@code name}
	 * query parameter returns HTTP 400 Bad Request.
	 *
	 * <p>The controller's {@code @RequestParam(name = "name") String name} makes
	 * the parameter required by default. Spring MVC automatically returns 400 Bad
	 * Request when a required query parameter is missing, with no explicit
	 * controller logic needed.</p>
	 */
	@Test
	void testSearchByName_MissingParam() throws Exception {
		// Act & Assert: Perform GET request WITHOUT name parameter
		mockMvc.perform(get("/product/search")
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}
}
