package com.promoquoter.promotion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.common.web.PageResponse;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.product.ProductService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final ProductService productService;

    @Transactional
    public List<PromotionResponse> createAll(List<CreatePromotionRequest> requests) {
        rejectUnknownProducts(requests);
        List<Promotion> promotions = requests.stream().map(CreatePromotionRequest::toEntity).toList();
        return promotionRepository.saveAll(promotions).stream().map(PromotionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> list(PromotionType type, Boolean active, Pageable pageable) {
        return PageResponse.of(promotionRepository.search(type, active, pageable), PromotionResponse::from);
    }

    private void rejectUnknownProducts(List<CreatePromotionRequest> requests) {
        Set<UUID> referenced = requests.stream()
                .map(CreatePromotionRequest::referencedProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (referenced.isEmpty()) {
            return;
        }
        Set<UUID> existing = productService.findExistingIds(referenced);
        List<FieldViolation> violations = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            UUID productId = requests.get(index).referencedProductId();
            if (productId != null && !existing.contains(productId)) {
                violations.add(new FieldViolation("[" + index + "].productId", "Product " + productId + " does not exist"));
            }
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.UNKNOWN_PRODUCT, "Promotion references products that do not exist",
                    violations);
        }
    }
}
