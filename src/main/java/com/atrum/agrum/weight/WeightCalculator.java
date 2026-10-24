package com.atrum.agrum.weight;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weight arithmetic from FSD sections 3.1–3.4. BigDecimal, 2 dp, HALF_UP throughout.
 */
public final class WeightCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    private WeightCalculator() {}

    /**
     * Estate.variancePercent is a Float. Going through its string form keeps 2.0 as 2.00;
     * BigDecimal.valueOf(double) of the float would give 2.0000000476...
     */
    public static BigDecimal variance(Float percent) {
        if (percent == null) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return new BigDecimal(Float.toString(percent)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * weight x (1 + variance / 100), rounded per record.
     * TODO(decision CL-01): the FSD proposes rounding each record and adding the rounded values;
     *  if totals should be rounded once instead, change this and DailyTotal together.
     */
    public static BigDecimal afterVariance(BigDecimal weightKg, BigDecimal variancePercent) {
        // weight x (100 + variance) / 100, so the only rounding is the final one
        return weightKg.multiply(HUNDRED.add(variancePercent)).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    // WE-06 / WH-06: greater than 0 with at most 2 decimals
    public static boolean isValidWeight(BigDecimal weightKg) {
        return weightKg != null && weightKg.signum() > 0 && weightKg.stripTrailingZeros().scale() <= SCALE;
    }

    /**
     * Half / Full Day and overweight (3.3) from the estate standards at run time (D-05).
     * total >= full: Full Day, overweight = total - full; otherwise Half Day, overweight = max(0, total - half).
     */
    public static DayResult classify(BigDecimal dailyTotalKg, Integer fullDayQtyKg, Integer halfDayQtyKg) {
        BigDecimal total = dailyTotalKg.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal full = BigDecimal.valueOf(fullDayQtyKg);
        if (total.compareTo(full) >= 0) {
            return new DayResult(DayClassification.FULL_DAY, total.subtract(full));
        }
        BigDecimal overHalf = total.subtract(BigDecimal.valueOf(halfDayQtyKg));
        return new DayResult(DayClassification.HALF_DAY,
                overHalf.signum() > 0 ? overHalf : BigDecimal.ZERO.setScale(SCALE));
    }

    public record DayResult(DayClassification classification, BigDecimal overweightKg) {}
}
