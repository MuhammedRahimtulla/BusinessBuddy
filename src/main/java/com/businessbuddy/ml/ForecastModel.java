package com.businessbuddy.ml;

import java.time.LocalDate;
import java.util.List;

/**
 * A daily demand forecasting model (strategy interface in Figure 4.6). The baseline and the
 * trained model implement the same interface so they can be evaluated on the same test days.
 *
 * <p>Refinement of the class diagram: {@code predict} receives the history known before the
 * target date. This makes the "one step ahead, using only earlier data" rule of 4.6.12
 * explicit, because a model can never see data on or after the date it predicts.</p>
 */
public interface ForecastModel {

    String name();

    String version();

    /** Fits the model on a gap-free daily history ordered by date. */
    void train(List<DemandPoint> history);

    /**
     * Predicts units for {@code date}. {@code known} is the gap-free history of all days
     * before {@code date}; it must end on the day before {@code date}.
     */
    double predict(LocalDate date, List<DemandPoint> known);
}
