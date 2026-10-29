package com.promoquoter.promotion.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;
import com.promoquoter.common.web.PageResponse;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.RuleKind;
import com.promoquoter.product.domain.ProductService;
import com.promoquoter.promotion.dto.CreatePromotionRequest;
import com.promoquoter.promotion.dto.PromotionResponse;
import com.promoquoter.promotion.dto.RuleRequest;
import com.promoquoter.promotion.dto.UpdatePromotionRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromotionService {

    public static final int MAX_ACTIVE_PROMOTIONS = 500;

    private final PromotionRepository promotionRepository;
    private final ProductService productService;

    @Transactional
    public List<PromotionResponse> createAll(List<CreatePromotionRequest> requests) {
        List<List<RuleParser.ProductReference>> ruleReferences = validateRules(requests, index -> "[" + index + "]");
        rejectUnknownProducts(requests, ruleReferences, index -> "[" + index + "]");
        requireRoomForActive(requests.stream().filter(CreatePromotionRequest::active).count());
        List<Promotion> promotions = requests.stream().map(PromotionMapper::toEntity).toList();
        return promotionRepository.saveAll(promotions).stream().map(PromotionMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RuleDefinition> activeDefinitions() {
        return promotionRepository.findByActiveTrue().stream().map(Promotion::toDefinition).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> list(PromotionType type, Boolean active, RuleKind kind, Pageable pageable) {
        return PageResponse.of(promotionRepository.search(type, active, kind, pageable), PromotionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PromotionResponse get(UUID id) {
        return PromotionMapper.toResponse(find(id));
    }

    @Transactional
    public PromotionResponse update(UUID id, UpdatePromotionRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                    List.of(new FieldViolation("body", "At least one field must be provided")));
        }
        Promotion promotion = find(id);
        if (Boolean.TRUE.equals(request.active()) && !promotion.isActive()) {
            requireRoomForActive(1);
        }
        promotion.update(request.name(), request.active(), request.priority(), request.eligibleSegments());
        return PromotionMapper.toResponse(promotionRepository.saveAndFlush(promotion));
    }

    @Transactional
    public void delete(UUID id) {
        promotionRepository.delete(find(id));
    }

    private Promotion find(UUID id) {
        return promotionRepository.findById(id).orElseThrow(
                () -> new ApiException(ErrorCode.PROMOTION_NOT_FOUND, "Promotion " + id + " was not found"));
    }

    public record PreviewRule(PromotionResponse response, RuleDefinition definition) {
    }

    @Transactional(readOnly = true)
    public PreviewRule previewRule(CreatePromotionRequest request) {
        List<CreatePromotionRequest> single = List.of(request);
        List<List<RuleParser.ProductReference>> references = validateRules(single, index -> "rule");
        rejectUnknownProducts(single, references, index -> "rule");
        Promotion promotion = PromotionMapper.toEntity(request);
        return new PreviewRule(PromotionMapper.toResponse(promotion), promotion.toDefinition());
    }

    private void requireRoomForActive(long additional) {
        long active = promotionRepository.countByActiveTrue();
        if (active + additional > MAX_ACTIVE_PROMOTIONS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed", List.of(new FieldViolation(
                    "promotions", "At most " + MAX_ACTIVE_PROMOTIONS + " promotions can be active at once, "
                            + active + " are active")));
        }
    }

    private List<List<RuleParser.ProductReference>> validateRules(List<CreatePromotionRequest> requests,
            IntFunction<String> path) {
        List<FieldViolation> violations = new ArrayList<>();
        List<List<RuleParser.ProductReference>> references = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            if (requests.get(index) instanceof RuleRequest rule) {
                ParsedRule parsed = RuleRequestParser.parse(rule, path.apply(index), violations);
                references.add(parsed == null ? List.of() : parsed.references());
            } else {
                references.add(List.of());
            }
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed", violations);
        }
        return references;
    }

    private void rejectUnknownProducts(List<CreatePromotionRequest> requests,
            List<List<RuleParser.ProductReference>> ruleReferences, IntFunction<String> path) {
        Set<UUID> referenced = new java.util.HashSet<>();
        requests.stream().map(CreatePromotionRequest::referencedProductId).filter(Objects::nonNull)
                .forEach(referenced::add);
        ruleReferences.forEach(list -> list.forEach(reference -> referenced.add(reference.productId())));
        if (referenced.isEmpty()) {
            return;
        }
        Set<UUID> existing = productService.findExistingIds(referenced);
        List<FieldViolation> violations = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            UUID productId = requests.get(index).referencedProductId();
            if (productId != null && !existing.contains(productId)) {
                violations.add(new FieldViolation(path.apply(index) + ".productId", "Product " + productId + " does not exist"));
            }
            for (RuleParser.ProductReference reference : ruleReferences.get(index)) {
                if (!existing.contains(reference.productId())) {
                    violations.add(new FieldViolation(reference.path(),
                            "Product " + reference.productId() + " does not exist"));
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.UNKNOWN_PRODUCT, "Promotion references products that do not exist",
                    violations);
        }
    }
}
