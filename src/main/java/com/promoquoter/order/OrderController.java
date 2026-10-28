package com.promoquoter.order;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.promoquoter.common.web.PageRequestFactory;
import com.promoquoter.common.web.PageResponse;
import com.promoquoter.common.web.SortPolicy;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final SortPolicy SORT_POLICY = SortPolicy.of("createdAt", Sort.Direction.DESC, "createdAt",
            "total");

    private final OrderService orderService;

    @GetMapping
    public PageResponse<OrderResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequestFactory.DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort) {
        return orderService.list(PageRequestFactory.create(page, size, sort, SORT_POLICY));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id);
    }
}
