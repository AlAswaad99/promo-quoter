package com.promoquoter.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({"1.005,1.01", "1.004,1.00", "2.5,2.50", "0.125,0.13", "10,10.00", "-1.005,-1.01"})
    void roundsHalfUpToCents(String input, String expected) {
        assertThat(Money.round(new BigDecimal(input))).isEqualByComparingTo(expected).hasScaleOf(2);
    }

    @ParameterizedTest
    @CsvSource({"33.33,15,5.00", "10.00,10,1.00", "0.05,50,0.03", "100.00,100,100.00", "19.99,0,0.00"})
    void percentOfRoundsHalfUp(String amount, String percent, String expected) {
        assertThat(Money.percentOf(new BigDecimal(amount), new BigDecimal(percent)))
                .isEqualByComparingTo(expected).hasScaleOf(2);
    }

    @ParameterizedTest
    @CsvSource({"32.40,1,3,10.80", "10.00,1,3,3.33", "10.00,2,3,6.67", "5.00,0,3,0.00", "7.00,3,3,7.00"})
    void proportionRoundsHalfUp(String amount, long numerator, long denominator, String expected) {
        assertThat(Money.proportion(new BigDecimal(amount), numerator, denominator))
                .isEqualByComparingTo(expected).hasScaleOf(2);
    }

    @Test
    void zeroHasCentScale() {
        assertThat(Money.ZERO).isEqualByComparingTo("0").hasScaleOf(2);
    }

    @Test
    void moneyFieldsSerializeAsTwoDecimalStrings() throws Exception {
        record Priced(@MoneyField BigDecimal price, BigDecimal percent) {
        }

        String json = new ObjectMapper().writeValueAsString(new Priced(new BigDecimal("12.5"), new BigDecimal("10")));

        assertThat(json).isEqualTo("{\"price\":\"12.50\",\"percent\":10}");
    }
}
