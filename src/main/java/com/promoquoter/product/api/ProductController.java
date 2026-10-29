package com.promoquoter.product.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.promoquoter.common.web.PageRequestFactory;
import com.promoquoter.common.web.PageResponse;
import com.promoquoter.common.web.SortPolicy;
import com.promoquoter.product.domain.ProductService;
import com.promoquoter.product.dto.CreateProductRequest;
import com.promoquoter.product.dto.ProductResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private static final SortPolicy SORT_POLICY = SortPolicy.of("createdAt", Sort.Direction.DESC, "name", "price",
            "createdAt");

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProductResponse> create(
            @RequestBody @Size(min = 1, max = 100) List<@NotNull @Valid CreateProductRequest> products) {
        return productService.createAll(products);
    }

    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequestFactory.DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String category) {
        return productService.list(category, PageRequestFactory.create(page, size, sort, SORT_POLICY));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(id);
    }
}
