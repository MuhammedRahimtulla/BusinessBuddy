package com.businessbuddy.ml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests of the machine learning layer (spec 16, "Machine learning tests").
 * The series are synthetic and generated inside the test; they test the code, not the
 * real-world accuracy of the model.
 */
class ForecastModelTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 5);   // a Monday

    /** Weekly pattern: quiet weekdays, busy weekends, plus small seeded noise. */
    static List<DemandPoint> weeklySeries(int days, long seed) {
        Random random = new Random(seed);
        List<DemandPoint> series = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = START.plusDays(i);
            double base = switch (d.getDayOfWeek()) {
                case SATURDAY -> 40;
                case SUNDAY -> 30;
                case FRIDAY -> 25;
                default -> 10;
            };
            series.add(new DemandPoint(d, Math.max(0, base + random.nextGaussian() * 2)));
        }
        return series;
    }

    @Test
    @DisplayName("TC-ML-01: MAE and RMSE are calculated correctly")
    void errorMetrics() {
        List<Double> actual = List.of(10.0, 20.0, 30.0);
        List<Double> predicted = List.of(12.0, 18.0, 33.0);
        assertThat(ForecastEvaluator.mae(actual, predicted)).isCloseTo(7.0 / 3, within(1e-9));
        assertThat(ForecastEvaluator.rmse(actual, predicted)).isCloseTo(Math.sqrt(17.0 / 3), within(1e-9));
    }

    @Test
    @DisplayName("TC-ML-02: moving average uses only the last N known days")
    void movingAverage() {
        List<DemandPoint> known = List.of(new DemandPoint(START, 100), new DemandPoint(START.plusDays(1), 2),
                new DemandPoint(START.plusDays(2), 4));
        MovingAverageForecaster ma = new MovingAverageForecaster(2);
        assertThat(ma.predict(START.plusDays(3), known)).isEqualTo(3.0);
        assertThat(ma.predict(START, List.of())).isZero();
    }

    @Test
    @DisplayName("TC-ML-03: Tribuo regression trains and beats the baseline on a strong weekly pattern")
    void regressionBeatsBaselineOnWeeklyPattern() {
        List<DemandPoint> history = weeklySeries(120, 7L);
        ForecastEvaluator.Result result = ForecastEvaluator.evaluate(history, 14,
                new MovingAverageForecaster(28), new RegressionForecaster());
        assertThat(result.candidateMae()).isLessThan(result.baselineMae());
        assertThat(result.selectedModel()).isEqualTo("REGRESSION");
    }

    @Test
    @DisplayName("TC-ML-04: the test period is strictly after the training period (chronological split)")
    void chronologicalSplit() {
        List<DemandPoint> history = weeklySeries(70, 3L);
        ForecastEvaluator.Result r = ForecastEvaluator.evaluate(history, 14,
                new MovingAverageForecaster(28), new RegressionForecaster());
        assertThat(r.trainEnd()).isBefore(r.testStart());
        assertThat(r.testEnd()).isEqualTo(history.get(history.size() - 1).date());
        assertThat(r.actual()).hasSize(14);
    }

    @Test
    @DisplayName("TC-ML-05: a series of zero sales gives zero forecasts, not an error")
    void zeroSales() {
        List<DemandPoint> zeros = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            zeros.add(new DemandPoint(START.plusDays(i), 0));
        }
        ForecastEvaluator.Result r = ForecastEvaluator.evaluate(zeros, 14,
                new MovingAverageForecaster(28), new RegressionForecaster());
        assertThat(r.baselineMae()).isZero();
        assertThat(r.candidatePredictions()).allSatisfy(p -> assertThat(p).isGreaterThanOrEqualTo(0.0));
    }

    @Test
    @DisplayName("TC-ML-06: regression refuses to train on too little history")
    void insufficientHistory() {
        assertThatThrownBy(() -> new RegressionForecaster().train(weeklySeries(30, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
