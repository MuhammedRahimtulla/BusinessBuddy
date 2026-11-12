# Model evaluation: sample-data run of 26 October 2026

Source: the Forecasts page of a fresh local start (`java -jar target/businessbuddy.jar`) with
120 days of **generated demonstration data** (Sunrise Bakery, sample data). These figures show
that the evaluation pipeline works; they say nothing about accuracy on a real business.

Training period 2026-06-28 to 2026-10-11, test period 2026-10-12 to 2026-10-25 (14 days, one step
ahead). Errors are in units per day. The model with the lower MAE is selected.

| Product | Model MAE | Model RMSE | Baseline MAE | Baseline RMSE | Selected |
|---|---|---|---|---|---|
| White bread loaf | 3.5462 | 4.4882 | 6.1199 | 7.5837 | REGRESSION |
| Still water 500 ml | 4.1461 | 5.1023 | 3.6327 | 4.4667 | MOVING_AVERAGE_28 |
| Orange juice 330 ml | 1.9691 | 2.3383 | 2.8036 | 3.1035 | REGRESSION |
| Chocolate cake slice | 2.5676 | 3.9673 | 4.6224 | 6.7446 | REGRESSION |
| Blueberry muffin | 4.6389 | 7.0800 | 4.8061 | 6.8402 | REGRESSION |

Observation: for the blueberry muffin the regression had the lower MAE but a higher RMSE than the
baseline, so it makes fewer small errors but some larger ones. The selection rule uses MAE only
(4.6.12); using both measures is noted as an improvement.
