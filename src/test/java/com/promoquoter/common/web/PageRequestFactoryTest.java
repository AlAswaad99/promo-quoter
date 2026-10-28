package com.promoquoter.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.promoquoter.common.error.ApiException;
import com.promoquoter.common.error.ErrorCode;

class PageRequestFactoryTest {

    private static final SortPolicy POLICY = SortPolicy.of("createdAt", Sort.Direction.DESC, "name", "price",
            "createdAt");

    @Test
    void usesDefaultSortWhenNoneGiven() {
        Pageable pageable = PageRequestFactory.create(0, 20, null, POLICY);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().toList()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    void blankSortFallsBackToDefault() {
        Pageable pageable = PageRequestFactory.create(0, 20, "  ", POLICY);

        assertThat(pageable.getSort().toList()).first().isEqualTo(Sort.Order.desc("createdAt"));
    }

    @Test
    void parsesFieldAndDirection() {
        Pageable pageable = PageRequestFactory.create(2, 10, "price,desc", POLICY);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getSort().toList()).containsExactly(Sort.Order.desc("price"), Sort.Order.asc("id"));
    }

    @Test
    void directionDefaultsToAscendingAndIsCaseInsensitive() {
        assertThat(PageRequestFactory.create(0, 5, "name", POLICY).getSort().toList()).first()
                .isEqualTo(Sort.Order.asc("name"));
        assertThat(PageRequestFactory.create(0, 5, "name,DESC", POLICY).getSort().toList()).first()
                .isEqualTo(Sort.Order.desc("name"));
    }

    @Test
    void capsPageSizeAtMaximum() {
        assertThat(PageRequestFactory.create(0, 5000, null, POLICY).getPageSize()).isEqualTo(100);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> PageRequestFactory.create(-1, 20, null, POLICY))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void rejectsNonPositiveSize() {
        assertThatThrownBy(() -> PageRequestFactory.create(0, 0, null, POLICY))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void rejectsSortFieldOutsideAllowList() {
        assertThatThrownBy(() -> PageRequestFactory.create(0, 20, "password,asc", POLICY))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_SORT);
                    assertThat(ex.getMessage()).contains("createdAt", "name", "price");
                });
    }

    @Test
    void rejectsUnknownDirectionAndExtraParts() {
        assertThatThrownBy(() -> PageRequestFactory.create(0, 20, "name,sideways", POLICY))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_SORT));
        assertThatThrownBy(() -> PageRequestFactory.create(0, 20, "name,asc,extra", POLICY))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_SORT));
    }

    @Test
    void pageResponseMapsContentAndMetadata() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(1, 3), 8);

        PageResponse<String> response = PageResponse.of(page, number -> "n" + number);

        assertThat(response.content()).containsExactly("n1", "n2", "n3");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.totalElements()).isEqualTo(8);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
