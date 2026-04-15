package com.jspider.spring_boot_simple_crud_with_mysql.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Integer> {

	List<Product> findByName(String name);
	
	@Query(value = "select * from product where price=?",nativeQuery = true)
	List<Product> getProductByPrice(double price);
	
	@Query(value = "delete from product where price=?",nativeQuery = true)
	@Modifying
	@Transactional
	void deleteProductByPrice(double price);
}
