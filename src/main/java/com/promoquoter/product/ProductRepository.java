package com.promoquoter.product;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByCategory(String category, Pageable pageable);
}
