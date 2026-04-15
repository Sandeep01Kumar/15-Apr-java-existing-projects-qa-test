package com.jspider.spring_boot_simple_crud_with_mysql.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.Product;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.ProductRepository;

/**
 * Unit tests for the {@link ProductDao#searchProductByNameDao(String)} method.
 *
 * <p>Uses pure Mockito (no Spring context) to verify that the DAO correctly
 * delegates search calls to {@link ProductRepository#findByNameContainingIgnoreCase(String)}
 * and returns results unchanged.</p>
 *
 * <p>Test coverage includes:
 * <ul>
 *   <li>Delegation verification — correct repository method is called with exact argument</li>
 *   <li>Result pass-through — DAO returns the repository result list without modification</li>
 *   <li>Empty result handling — DAO returns a non-null empty list when no products match</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ProductDaoSearchTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductDao productDao;

    /**
     * Verifies that {@code searchProductByNameDao} correctly delegates to
     * {@code productRepository.findByNameContainingIgnoreCase} with the exact
     * argument passed by the caller.
     */
    @Test
    void testSearchProductByNameDao_DelegatesToRepository() {
        // Arrange
        String searchName = "laptop";
        when(productRepository.findByNameContainingIgnoreCase(searchName))
                .thenReturn(Collections.emptyList());

        // Act
        productDao.searchProductByNameDao(searchName);

        // Assert — verify the repository method was called with the exact argument
        verify(productRepository).findByNameContainingIgnoreCase("laptop");
    }

    /**
     * Verifies that the DAO correctly returns the list of products from the
     * repository without any filtering, transformation, or mutation.
     */
    @Test
    void testSearchProductByNameDao_ReturnsResults() {
        // Arrange
        Product product1 = new Product();
        product1.setId(1);
        product1.setName("Laptop Pro");
        product1.setColor("Silver");
        product1.setPrice(999.99);

        Product product2 = new Product();
        product2.setId(2);
        product2.setName("Laptop Air");
        product2.setColor("Gold");
        product2.setPrice(1299.99);

        List<Product> expectedProducts = Arrays.asList(product1, product2);
        when(productRepository.findByNameContainingIgnoreCase("laptop"))
                .thenReturn(expectedProducts);

        // Act
        List<Product> result = productDao.searchProductByNameDao("laptop");

        // Assert — verify the result list is returned unchanged
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Laptop Pro", result.get(0).getName());
        assertEquals("Laptop Air", result.get(1).getName());
    }

    /**
     * Verifies correct behavior when the repository returns an empty list,
     * ensuring the DAO does not throw exceptions or return null for
     * a no-match scenario.
     */
    @Test
    void testSearchProductByNameDao_EmptyResult() {
        // Arrange
        when(productRepository.findByNameContainingIgnoreCase("nonexistent"))
                .thenReturn(Collections.emptyList());

        // Act
        List<Product> result = productDao.searchProductByNameDao("nonexistent");

        // Assert — verify non-null, empty list is returned
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
