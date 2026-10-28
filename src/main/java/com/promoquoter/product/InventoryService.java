package com.promoquoter.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;
import com.promoquoter.common.error.FieldViolation;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;

    public record StockShortage(UUID productId, int requested, int available) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Map<UUID, Integer> quantities) {
        List<UUID> ids = quantities.keySet().stream().sorted().toList();
        Map<UUID, Product> locked = productRepository.lockAllByIdOrderById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        rejectMissing(ids, locked);
        rejectShortages(ids, quantities, locked);
        ids.forEach(id -> locked.get(id).decrementStock(quantities.get(id)));
    }

    private static void rejectMissing(List<UUID> ids, Map<UUID, Product> locked) {
        List<FieldViolation> missing = ids.stream()
                .filter(id -> !locked.containsKey(id))
                .map(id -> new FieldViolation("productId", "Product " + id + " was not found"))
                .toList();
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Some products in the cart were not found", missing);
        }
    }

    private static void rejectShortages(List<UUID> ids, Map<UUID, Integer> quantities, Map<UUID, Product> locked) {
        List<StockShortage> shortages = new ArrayList<>();
        for (UUID id : ids) {
            int requested = quantities.get(id);
            int available = locked.get(id).getStock();
            if (available < requested) {
                shortages.add(new StockShortage(id, requested, available));
            }
        }
        if (!shortages.isEmpty()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK,
                    "Not enough stock for " + shortages.size() + " product(s)", shortages);
        }
    }
}
