package com.atrum.agrum.weight;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WeightCalculatorTest {

    private static final BigDecimal PLUS_2 = new BigDecimal("2.00");
    private static final BigDecimal MINUS_2 = new BigDecimal("-2.00");

    @Test
    void TC59_25kgAtPlus2_is25_50() {
        assertThat(WeightCalculator.afterVariance(new BigDecimal("25.00"), PLUS_2)).isEqualTo("25.50");
    }

    @Test
    void TC60_25kgAtMinus2_is24_50() {
        assertThat(WeightCalculator.afterVariance(new BigDecimal("25.00"), MINUS_2)).isEqualTo("24.50");
    }

    @Test
    void afterVariance_examplesFromSection3_1() {
        assertThat(WeightCalculator.afterVariance(new BigDecimal("12.00"), PLUS_2)).isEqualTo("12.24");
        assertThat(WeightCalculator.afterVariance(new BigDecimal("13.00"), PLUS_2)).isEqualTo("13.26");
        assertThat(WeightCalculator.afterVariance(new BigDecimal("8.00"), MINUS_2)).isEqualTo("7.84");
        assertThat(WeightCalculator.afterVariance(new BigDecimal("9.00"), MINUS_2)).isEqualTo("8.82");
        assertThat(WeightCalculator.afterVariance(new BigDecimal("14.00"), PLUS_2)).isEqualTo("14.28");
        assertThat(WeightCalculator.afterVariance(new BigDecimal("18.00"), new BigDecimal("0.00"))).isEqualTo("18.00");
    }

    @Test
    void afterVariance_roundsHalfUpPerRecord() {
        // 12.25 x 1.02 = 12.495
        assertThat(WeightCalculator.afterVariance(new BigDecimal("12.25"), PLUS_2)).isEqualTo("12.50");
    }

    @Test
    void variance_keepsTheFloatsDecimalValue() {
        assertThat(WeightCalculator.variance(2.0f)).isEqualTo("2.00");
        assertThat(WeightCalculator.variance(-2.0f)).isEqualTo("-2.00");
        assertThat(WeightCalculator.variance(1.1f)).isEqualTo("1.10");
        assertThat(WeightCalculator.variance(null)).isEqualTo("0.00");
    }

    @Test
    void TC56_isValidWeight_needsMoreThanZeroAndAtMostTwoDecimals() {
        assertThat(WeightCalculator.isValidWeight(new BigDecimal("12.50"))).isTrue();
        assertThat(WeightCalculator.isValidWeight(new BigDecimal("12.500"))).isTrue();
        assertThat(WeightCalculator.isValidWeight(new BigDecimal("12.505"))).isFalse();
        assertThat(WeightCalculator.isValidWeight(BigDecimal.ZERO)).isFalse();
        assertThat(WeightCalculator.isValidWeight(new BigDecimal("-1.00"))).isFalse();
        assertThat(WeightCalculator.isValidWeight(null)).isFalse();
    }

    @Test
    void TC62_total25_50_isFullDayWith2_50Overweight() {
        WeightCalculator.DayResult result = WeightCalculator.classify(new BigDecimal("25.50"), 23, 12);
        assertThat(result.classification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(result.overweightKg()).isEqualTo("2.50");
    }

    @Test
    void TC63_total16_66_isHalfDayWith4_66Overweight() {
        WeightCalculator.DayResult result = WeightCalculator.classify(new BigDecimal("16.66"), 23, 12);
        assertThat(result.classification()).isEqualTo(DayClassification.HALF_DAY);
        assertThat(result.overweightKg()).isEqualTo("4.66");
    }

    @Test
    void TC64_total10_20_isHalfDayWithNoOverweight() {
        WeightCalculator.DayResult result = WeightCalculator.classify(new BigDecimal("10.20"), 23, 12);
        assertThat(result.classification()).isEqualTo(DayClassification.HALF_DAY);
        assertThat(result.overweightKg()).isEqualTo("0.00");
    }

    @Test
    void classify_exactlyFullDay_isFullDayWithNoOverweight() {
        WeightCalculator.DayResult result = WeightCalculator.classify(new BigDecimal("23.00"), 23, 12);
        assertThat(result.classification()).isEqualTo(DayClassification.FULL_DAY);
        assertThat(result.overweightKg()).isEqualTo("0.00");
    }

    @Test
    void classify_approvedCorrectionAndCancellationExamples() {
        // E001 correction 13 -> 14 approved: 12.24 + 14.28
        assertThat(WeightCalculator.classify(new BigDecimal("26.52"), 23, 12).overweightKg()).isEqualTo("3.52");
        // E001 weighing 2 cancelled instead: 12.24 only
        WeightCalculator.DayResult cancelled = WeightCalculator.classify(new BigDecimal("12.24"), 23, 12);
        assertThat(cancelled.classification()).isEqualTo(DayClassification.HALF_DAY);
        assertThat(cancelled.overweightKg()).isEqualTo("0.24");
    }
}
