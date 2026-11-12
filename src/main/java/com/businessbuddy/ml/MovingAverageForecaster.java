package com.businessbuddy.ml;

import java.time.LocalDate;
import java.util.List;

/**
 * Baseline model: the average of the last {@code windowDays} days (spec 9.1, "start with a
 * simple baseline"). It needs no training; the trained model must beat it to be used.
 */
public class MovingAverageForecaster implements ForecastModel {

    private final int windowDays;

    public MovingAverageForecaster(int windowDays) {
        if (windowDays <= 0) {
            throw new IllegalArgumentException("windowDays must be positive");
        }
        this.windowDays = windowDays;
    }

    @Override
    public String name() {
        return "MOVING_AVERAGE_" + windowDays;
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public void train(List<DemandPoint> history) {
        // Nothing to learn: the average is taken from the known history at prediction time.
    }

    @Override
    public double predict(LocalDate date, List<DemandPoint> known) {
        if (known.isEmpty()) {
            return 0.0;
        }
        int from = Math.max(0, known.size() - windowDays);
        double sum = 0.0;
        for (int i = from; i < known.size(); i++) {
            sum += known.get(i).units();
        }
        return sum / (known.size() - from);
    }
}
