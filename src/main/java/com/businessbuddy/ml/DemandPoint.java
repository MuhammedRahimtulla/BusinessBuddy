package com.businessbuddy.ml;

import java.time.LocalDate;

/**
 * Units of one product sold on one day. Days without sales are present with 0 units,
 * so a history is always a gap-free daily series (4.6.12: "fill every missing date").
 * Model maths uses double; stored forecasts are converted to BigDecimal.
 */
public record DemandPoint(LocalDate date, double units) {
}
