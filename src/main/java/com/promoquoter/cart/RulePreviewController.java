package com.promoquoter.cart;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/promotions/preview")
@RequiredArgsConstructor
public class RulePreviewController {

    private final RulePreviewService rulePreviewService;

    @PostMapping
    public RulePreviewResponse preview(@RequestBody @Valid RulePreviewRequest request) {
        return rulePreviewService.preview(request);
    }
}
