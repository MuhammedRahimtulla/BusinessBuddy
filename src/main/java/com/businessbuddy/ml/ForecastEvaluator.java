package com.businessbuddy.ml;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Chronological evaluation of a candidate model against the baseline (spec 9.1, 4.6.12).
 *
 * <p>The last {@code testDays} days are held out. Both models are trained on the earlier
 * days only, then predict each test day one step ahead from the days before it. Because
 * the test period is always after the training period, no future data leaks into training.
 * Errors are MAE and RMSE; MAPE is not used because many days have zero sales.</p>
 */
public final class ForecastEvaluator {

    private ForecastEvaluator() {
    }

    public record Result(LocalDate trainStart, LocalDate trainEnd, LocalDate testStart, LocalDate testEnd,
                         int sampleDays, double candidateMae, double candidateRmse, double baselineMae,
                         double baselineRmse, String selectedModel, List<Double> actual,
                         List<Double> candidatePredictions, List<Double> baselinePredictions) {

        /** The candidate is only used when it is more accurate than the baseline. */
        public boolean candidateSelected() {
            return candidateMae < baselineMae;
        }
    }

    public static Result evaluate(List<DemandPoint> history, int testDays, ForecastModel baseline,
                                  ForecastModel candidate) {
        if (testDays <= 0 || history.size() <= testDays) {
            throw new IllegalArgumentException("History must be longer than the test period");
        }
        int split = history.size() - testDays;
        List<DemandPoint> train = history.subList(0, split);
        baseline.train(train);
        candidate.train(train);

        List<Double> actual = new ArrayList<>();
        List<Double> cand = new ArrayList<>();
        List<Double> base = new ArrayList<>();
        for (int i = split; i < history.size(); i++) {
            List<DemandPoint> known = history.subList(0, i);         // only days before the target
            LocalDate day = history.get(i).date();
            actual.add(history.get(i).units());
            cand.add(Math.max(0.0, candidate.predict(day, known)));
            base.add(Math.max(0.0, baseline.predict(day, known)));
        }
        double cMae = mae(actual, cand);
        double bMae = mae(actual, base);
        String selected = cMae < bMae ? candidate.name() : baseline.name();
        return new Result(train.get(0).date(), train.get(train.size() - 1).date(), history.get(split).date(),
                history.get(history.size() - 1).date(), history.size(), cMae, rmse(actual, cand), bMae,
                rmse(actual, base), selected, actual, cand, base);
    }

    public static double mae(List<Double> actual, List<Double> predicted) {
        check(actual, predicted);
        double sum = 0;
        for (int i = 0; i < actual.size(); i++) {
            sum += Math.abs(actual.get(i) - predicted.get(i));
        }
        return sum / actual.size();
    }

    public static double rmse(List<Double> actual, List<Double> predicted) {
        check(actual, predicted);
        double sum = 0;
        for (int i = 0; i < actual.size(); i++) {
            double e = actual.get(i) - predicted.get(i);
            sum += e * e;
        }
        return Math.sqrt(sum / actual.size());
    }

    private static void check(List<Double> actual, List<Double> predicted) {
        if (actual.isEmpty() || actual.size() != predicted.size()) {
            throw new IllegalArgumentException("Actual and predicted series must be non-empty and equal in length");
        }
    }
}
