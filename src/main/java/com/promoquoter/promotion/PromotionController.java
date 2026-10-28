package com.promoquoter.promotion;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.promoquoter.common.web.PageRequestFactory;
import com.promoquoter.common.web.PageResponse;
import com.promoquoter.common.web.SortPolicy;
import com.promoquoter.pricing.PromotionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private static final SortPolicy SORT_POLICY = SortPolicy.of("createdAt", Sort.Direction.DESC, "name", "priority",
            "createdAt");

    private final PromotionService promotionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<PromotionResponse> create(
            @RequestBody @Size(min = 1, max = 100) List<@NotNull @Valid CreatePromotionRequest> promotions) {
        return promotionService.createAll(promotions);
    }

    @GetMapping
    public PageResponse<PromotionResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequestFactory.DEFAULT_SIZE) int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) PromotionType type,
            @RequestParam(required = false) Boolean active) {
        return promotionService.list(type, active, PageRequestFactory.create(page, size, sort, SORT_POLICY));
    }
}
