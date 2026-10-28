package com.promoquoter.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public List<ProductResponse> createAll(List<CreateProductRequest> requests) {
        List<Product> products = requests.stream()
                .map(request -> new Product(request.name(), request.category(), request.price(), request.stock()))
                .toList();
        return productRepository.saveAll(products).stream().map(ProductResponse::from).toList();
    }
}
