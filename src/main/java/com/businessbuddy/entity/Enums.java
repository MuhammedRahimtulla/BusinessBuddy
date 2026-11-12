package com.businessbuddy.entity;

/**
 * Holder for the small enumerations used by the entities. Each enum's values match the
 * CHECK constraint on its column in V1__core_schema.sql, so an invalid value is rejected
 * both by Java and by the database.
 */
public final class Enums {

    private Enums() {
    }

    /** Decision D8: exactly one role per user. */
    public enum Role { ADMINISTRATOR, OWNER, MANAGER, EMPLOYEE }

    public enum Dimension { MASS, VOLUME, COUNT, LENGTH }

    public enum CategoryType { PRODUCT, INGREDIENT }

    public enum ProductType { MANUFACTURED, RESALE }

    public enum PurchaseStatus { DRAFT, RECEIVED, CANCELLED }

    public enum BatchStatus { DRAFT, CONFIRMED, CANCELLED }

    public enum SaleStatus { DRAFT, CONFIRMED, VOIDED }

    public enum PaymentMethod { CASH, CARD, EFT, MOBILE, OTHER }

    /**
     * Ledger movement types (8.7). The sign rule is enforced by ck_movement_sign:
     * receipts and production output increase stock; consumption, sales and waste reduce it;
     * returns, adjustments and reversals may go either way.
     */
    public enum MovementType {
        PURCHASE_RECEIPT, PRODUCTION_CONSUMPTION, PRODUCTION_OUTPUT, SALE, RETURN, WASTE, ADJUSTMENT, REVERSAL
    }

    public enum SourceType { PURCHASE_ITEM, PRODUCTION_CONSUMPTION, PRODUCTION_BATCH, SALE_ITEM, MANUAL }

    public enum ForecastType { DEMAND, STOCKOUT }

    public enum ForecastStatus { ACTIVE, SUPERSEDED, INSUFFICIENT_DATA }

    public enum NotificationType {
        LOW_STOCK, EXPIRING_SOON, EXPIRED, COST_INCREASE, PREDICTED_STOCKOUT, FORECAST_CHANGE, ADMIN
    }

    /** Separates facts from predictions, as section 8.19 of the specification requires. */
    public enum NotificationNature { OBSERVED, PREDICTED }

    public enum NotificationStatus { OPEN, READ, RESOLVED }
}
