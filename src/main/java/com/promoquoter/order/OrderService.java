package com.promoquoter.order;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.web.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order " + id + " was not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Pageable pageable) {
        return PageResponse.of(orderRepository.findAll(pageable), OrderResponse::from);
    }
}
