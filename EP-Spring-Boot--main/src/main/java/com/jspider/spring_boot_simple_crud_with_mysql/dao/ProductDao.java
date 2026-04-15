package com.jspider.spring_boot_simple_crud_with_mysql.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.Product;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.ProductRepository;

@Repository
public class ProductDao {

	@Autowired
	ProductRepository productRepository;

	public Product saveProductDao(Product product) {
		return productRepository.save(product);
	}

	public List<Product> saveMultipleProductDao(List<Product> product) {
		return productRepository.saveAll(product);
	}

	public List<Product> displayAllProductDao() {
		return productRepository.findAll();
	}

	public Product getProductByIdDao(Integer id) {

		Optional<Product> optional = productRepository.findById(id);

		return optional.isPresent() ? optional.get() : null;

	}

	public List<Product> getProductByNameDao(String name) {
		
		return productRepository.findByName(name);

	}
	
	public List<Product> getProductByPriceDao(double price){
		return productRepository.getProductByPrice(price);
	}
	
	public void deleteProductByPriceDao(double price) {
		
		productRepository.deleteProductByPrice(price);
	}
	
	public Product updateProductDao(Product product, Integer id) {
	    Optional<Product> optional = productRepository.findById(id);

	    if (optional.isPresent()) {
	        Product existingProduct = optional.get();

	        // Do NOT update the ID; it should remain unchanged
	        existingProduct.setName(product.getName());
	        existingProduct.setColor(product.getColor());
	        existingProduct.setPrice(product.getPrice());

	        return productRepository.save(existingProduct);
	    } else {
	        // You can handle not found case as you wish
	        throw new RuntimeException("Product not found with ID: " + id);
	    }
	}
	
	 
	
	
}
