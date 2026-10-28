package com.promoquoter.product;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.text.Categories;
import com.promoquoter.common.web.PageResponse;

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

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Product " + id + " was not found"));
    }

    @Transactional(readOnly = true)
    public Set<UUID> findExistingIds(Collection<UUID> ids) {
        return productRepository.findAllById(ids).stream().map(Product::getId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<ProductSnapshot> findSnapshots(Collection<UUID> ids) {
        return productRepository.findAllById(ids).stream().map(ProductSnapshot::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String category, Pageable pageable) {
        Page<Product> page = category == null || category.isBlank()
                ? productRepository.findAll(pageable)
                : productRepository.findByCategory(Categories.normalize(category), pageable);
        return PageResponse.of(page, ProductResponse::from);
    }
}
