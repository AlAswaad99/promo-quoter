package com.promoquoter.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CategoriesTest {

    @ParameterizedTest
    @CsvSource({"electronics,ELECTRONICS", "'  Kitchen ',KITCHEN", "HOME,HOME", "home goods,HOME GOODS"})
    void trimsAndUpperCases(String input, String expected) {
        assertThat(Categories.normalize(input)).isEqualTo(expected);
    }
}
