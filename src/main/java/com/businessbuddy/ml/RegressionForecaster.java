package com.businessbuddy.ml;

import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Prediction;
import org.tribuo.impl.ArrayExample;
import org.tribuo.math.optimisers.AdaGrad;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.sgd.linear.LinearSGDTrainer;
import org.tribuo.regression.sgd.objectives.SquaredLoss;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Trained demand model: linear regression fitted with Tribuo's stochastic gradient trainer
 * (decision D10). Features, as listed in 4.6.12:
 * <ul>
 *   <li>day of week (7 indicator features),</li>
 *   <li>month (as sine and cosine, so December is next to January),</li>
 *   <li>units sold 7 days earlier, the 7-day average and the 28-day average.</li>
 * </ul>
 * The three demand features are standardised with the training mean and standard deviation.
 * A row needs 28 earlier days, so the first 28 days of history only provide lag values.
 */
public class RegressionForecaster implements ForecastModel {

    static final int MAX_LAG = 28;
    private static final String[] FEATURE_NAMES = {
            "dow_1", "dow_2", "dow_3", "dow_4", "dow_5", "dow_6", "dow_7",
            "month_sin", "month_cos", "lag_7", "avg_7", "avg_28"};

    private final RegressionFactory factory = new RegressionFactory();
    private final int epochs;
    private final long seed;
    private Model<Regressor> model;
    private final double[] mean = new double[3];
    private final double[] std = {1, 1, 1};

    public RegressionForecaster() {
        this(150, 42L);
    }

    public RegressionForecaster(int epochs, long seed) {
        this.epochs = epochs;
        this.seed = seed;
    }

    @Override
    public String name() {
        return "REGRESSION";
    }

    @Override
    public String version() {
        return "tribuo-sgd-1";
    }

    @Override
    public void train(List<DemandPoint> history) {
        List<double[]> raw = new ArrayList<>();
        List<Double> targets = new ArrayList<>();
        for (int i = MAX_LAG; i < history.size(); i++) {
            raw.add(rawDemandFeatures(history.subList(0, i)));
            targets.add(history.get(i).units());
        }
        if (raw.size() < 7) {
            throw new IllegalArgumentException("At least " + (MAX_LAG + 7) + " days of history are needed");
        }
        for (int f = 0; f < 3; f++) {
            double sum = 0;
            for (double[] r : raw) {
                sum += r[f];
            }
            mean[f] = sum / raw.size();
            double sq = 0;
            for (double[] r : raw) {
                sq += (r[f] - mean[f]) * (r[f] - mean[f]);
            }
            double sd = Math.sqrt(sq / raw.size());
            std[f] = sd < 1e-9 ? 1.0 : sd;
        }
        MutableDataset<Regressor> data = new MutableDataset<>(
                new SimpleDataSourceProvenance("BusinessBuddy daily demand", factory), factory);
        for (int i = 0; i < raw.size(); i++) {
            LocalDate date = history.get(i + MAX_LAG).date();
            data.add(new ArrayExample<>(new Regressor("units", targets.get(i)), FEATURE_NAMES,
                    features(date, raw.get(i))));
        }
        LinearSGDTrainer trainer = new LinearSGDTrainer(new SquaredLoss(), new AdaGrad(0.1, 0.1), epochs, -1, 1, seed);
        model = trainer.train(data);
    }

    @Override
    public double predict(LocalDate date, List<DemandPoint> known) {
        if (model == null) {
            throw new IllegalStateException("The model has not been trained");
        }
        Prediction<Regressor> p = model.predict(new ArrayExample<>(RegressionFactory.UNKNOWN_REGRESSOR,
                FEATURE_NAMES, features(date, rawDemandFeatures(known))));
        return p.getOutput().getValues()[0];
    }

    /** lag 7, 7-day average and 28-day average, computed from the days before the target. */
    static double[] rawDemandFeatures(List<DemandPoint> known) {
        int n = known.size();
        double lag7 = n >= 7 ? known.get(n - 7).units() : average(known, n);
        return new double[]{lag7, average(known, 7), average(known, MAX_LAG)};
    }

    private static double average(List<DemandPoint> known, int days) {
        int from = Math.max(0, known.size() - days);
        if (known.size() - from == 0) {
            return 0.0;
        }
        double sum = 0;
        for (int i = from; i < known.size(); i++) {
            sum += known.get(i).units();
        }
        return sum / (known.size() - from);
    }

    private double[] features(LocalDate date, double[] raw) {
        double[] v = new double[FEATURE_NAMES.length];
        DayOfWeek dow = date.getDayOfWeek();
        v[dow.getValue() - 1] = 1.0;
        double angle = 2 * Math.PI * (date.getMonthValue() - 1) / 12.0;
        v[7] = Math.sin(angle);
        v[8] = Math.cos(angle);
        for (int f = 0; f < 3; f++) {
            v[9 + f] = (raw[f] - mean[f]) / std[f];
        }
        return v;
    }
}
