-- =====================================================================
-- BusinessBuddy: AI-Powered Business Cost and Inventory Management System
-- V1__core_schema.sql  (database: businessbuddy_db)
--
-- Implements the schema in Chapter 4, section 4.5 (Figure 4.11 and the
-- data dictionary in 4.5.4). Tables are created in dependency order.
-- =====================================================================

-- Reference data -------------------------------------------------------
CREATE TABLE business (
    business_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                   VARCHAR(150)  NOT NULL,
    email                  VARCHAR(255),
    phone                  VARCHAR(30),
    address                VARCHAR(300),
    currency_code          CHAR(3)       NOT NULL,
    time_zone              VARCHAR(64)   NOT NULL,
    default_tax_rate       NUMERIC(5,4)  NOT NULL DEFAULT 0
                           CHECK (default_tax_rate >= 0 AND default_tax_rate < 1),
    default_target_margin  NUMERIC(5,4)  NOT NULL DEFAULT 0
                           CHECK (default_target_margin >= 0 AND default_target_margin < 1),
    allow_negative_stock   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    user_id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id            BIGINT        REFERENCES business (business_id),
    full_name              VARCHAR(120)  NOT NULL,
    email                  VARCHAR(255)  NOT NULL,
    password_hash          VARCHAR(100)  NOT NULL,
    role                   VARCHAR(20)   NOT NULL
                           CHECK (role IN ('ADMINISTRATOR', 'OWNER', 'MANAGER', 'EMPLOYEE')),
    is_active              BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_login_attempts  INTEGER       NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until           TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_app_user_business CHECK (
        (role = 'ADMINISTRATOR' AND business_id IS NULL) OR
        (role <> 'ADMINISTRATOR' AND business_id IS NOT NULL))
);

CREATE TABLE audit_log (
    audit_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id  BIGINT       REFERENCES business (business_id),
    user_id      BIGINT       REFERENCES app_user (user_id),
    action       VARCHAR(40)  NOT NULL,
    entity_type  VARCHAR(40),
    entity_id    BIGINT,
    details      JSONB,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_business_date ON audit_log (business_id, created_at);

CREATE TABLE unit_of_measure (
    unit_code       VARCHAR(10)    PRIMARY KEY,
    name            VARCHAR(40)    NOT NULL,
    dimension       VARCHAR(10)    NOT NULL
                    CHECK (dimension IN ('MASS', 'VOLUME', 'COUNT', 'LENGTH')),
    factor_to_base  NUMERIC(18,6)  NOT NULL CHECK (factor_to_base > 0)
);

INSERT INTO unit_of_measure (unit_code, name, dimension, factor_to_base) VALUES
    ('g',     'Gram',       'MASS',   1),
    ('kg',    'Kilogram',   'MASS',   1000),
    ('ml',    'Millilitre', 'VOLUME', 1),
    ('l',     'Litre',      'VOLUME', 1000),
    ('unit',  'Unit',       'COUNT',  1),
    ('dozen', 'Dozen',      'COUNT',  12),
    ('m',     'Metre',      'LENGTH', 1);

CREATE TABLE category (
    category_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id    BIGINT       NOT NULL REFERENCES business (business_id),
    name           VARCHAR(80)  NOT NULL,
    category_type  VARCHAR(12)  NOT NULL CHECK (category_type IN ('PRODUCT', 'INGREDIENT')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_category UNIQUE (business_id, category_type, name)
);

CREATE TABLE supplier (
    supplier_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id     BIGINT        NOT NULL REFERENCES business (business_id),
    name            VARCHAR(150)  NOT NULL,
    contact_person  VARCHAR(120),
    email           VARCHAR(255),
    phone           VARCHAR(30),
    address         VARCHAR(300),
    notes           TEXT,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_supplier_name UNIQUE (business_id, name)
);

-- Stock items ----------------------------------------------------------
CREATE TABLE ingredient (
    ingredient_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id            BIGINT         NOT NULL REFERENCES business (business_id),
    category_id            BIGINT         REFERENCES category (category_id),
    preferred_supplier_id  BIGINT         REFERENCES supplier (supplier_id),
    name                   VARCHAR(120)   NOT NULL,
    description            VARCHAR(500),
    base_unit_code         VARCHAR(10)    NOT NULL REFERENCES unit_of_measure (unit_code),
    current_quantity       NUMERIC(14,3)  NOT NULL DEFAULT 0,
    average_unit_cost      NUMERIC(14,4)  NOT NULL DEFAULT 0 CHECK (average_unit_cost >= 0),
    min_stock_level        NUMERIC(14,3)  NOT NULL DEFAULT 0 CHECK (min_stock_level >= 0),
    reorder_quantity       NUMERIC(14,3)  CHECK (reorder_quantity > 0),
    expiry_tracked         BOOLEAN        NOT NULL DEFAULT FALSE,
    is_active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_ingredient_name UNIQUE (business_id, name)
);

CREATE INDEX ix_ingredient_business ON ingredient (business_id, is_active);

CREATE TABLE product (
    product_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id        BIGINT         NOT NULL REFERENCES business (business_id),
    category_id        BIGINT         REFERENCES category (category_id),
    name               VARCHAR(120)   NOT NULL,
    sku                VARCHAR(40),
    description        VARCHAR(500),
    product_type       VARCHAR(12)    NOT NULL CHECK (product_type IN ('MANUFACTURED', 'RESALE')),
    sale_unit_code     VARCHAR(10)    NOT NULL REFERENCES unit_of_measure (unit_code),
    selling_price      NUMERIC(12,2)  NOT NULL CHECK (selling_price >= 0),
    current_quantity   NUMERIC(14,3)  NOT NULL DEFAULT 0,
    average_unit_cost  NUMERIC(14,4)  NOT NULL DEFAULT 0 CHECK (average_unit_cost >= 0),
    min_stock_level    NUMERIC(14,3)  NOT NULL DEFAULT 0 CHECK (min_stock_level >= 0),
    is_active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_name UNIQUE (business_id, name),
    CONSTRAINT uq_product_sku  UNIQUE (business_id, sku)
);

CREATE INDEX ix_product_business ON product (business_id, is_active);

-- Recipes --------------------------------------------------------------
CREATE TABLE recipe_version (
    recipe_version_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id         BIGINT         NOT NULL REFERENCES product (product_id),
    version_no         INTEGER        NOT NULL CHECK (version_no > 0),
    name               VARCHAR(120)   NOT NULL,
    batch_output_qty   NUMERIC(14,3)  NOT NULL CHECK (batch_output_qty > 0),
    output_unit_code   VARCHAR(10)    NOT NULL REFERENCES unit_of_measure (unit_code),
    labour_cost        NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (labour_cost >= 0),
    packaging_cost     NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (packaging_cost >= 0),
    utilities_cost     NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (utilities_cost >= 0),
    overhead_fixed     NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (overhead_fixed >= 0),
    overhead_percent   NUMERIC(5,4)   NOT NULL DEFAULT 0
                       CHECK (overhead_percent >= 0 AND overhead_percent <= 1),
    preparation_notes  TEXT,
    is_active          BOOLEAN        NOT NULL DEFAULT FALSE,
    effective_from     DATE           NOT NULL,
    created_by         BIGINT         NOT NULL REFERENCES app_user (user_id),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_recipe_version UNIQUE (product_id, version_no)
);

CREATE UNIQUE INDEX uq_recipe_active ON recipe_version (product_id) WHERE is_active;

CREATE TABLE recipe_ingredient (
    recipe_ingredient_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipe_version_id     BIGINT         NOT NULL REFERENCES recipe_version (recipe_version_id),
    ingredient_id         BIGINT         NOT NULL REFERENCES ingredient (ingredient_id),
    quantity              NUMERIC(14,3)  NOT NULL CHECK (quantity > 0),
    unit_code             VARCHAR(10)    NOT NULL REFERENCES unit_of_measure (unit_code),
    waste_percent         NUMERIC(5,4)   NOT NULL DEFAULT 0
                          CHECK (waste_percent >= 0 AND waste_percent < 1),
    notes                 VARCHAR(300),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_recipe_ingredient UNIQUE (recipe_version_id, ingredient_id)
);

-- Purchasing -----------------------------------------------------------
CREATE TABLE purchase (
    purchase_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id       BIGINT         NOT NULL REFERENCES business (business_id),
    supplier_id       BIGINT         NOT NULL REFERENCES supplier (supplier_id),
    reference_no      VARCHAR(50)    NOT NULL,
    purchase_date     DATE           NOT NULL,
    received_date     DATE,
    status            VARCHAR(10)    NOT NULL DEFAULT 'DRAFT'
                      CHECK (status IN ('DRAFT', 'RECEIVED', 'CANCELLED')),
    currency_code     CHAR(3)        NOT NULL,
    subtotal          NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    discount_amount   NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    additional_costs  NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (additional_costs >= 0),
    total_amount      NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    notes             TEXT,
    created_by        BIGINT         NOT NULL REFERENCES app_user (user_id),
    received_by       BIGINT         REFERENCES app_user (user_id),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchase_reference UNIQUE (business_id, supplier_id, reference_no),
    CONSTRAINT ck_purchase_total CHECK (total_amount = subtotal - discount_amount + additional_costs),
    CONSTRAINT ck_purchase_received CHECK (
        status <> 'RECEIVED' OR (received_date IS NOT NULL AND received_by IS NOT NULL))
);

CREATE INDEX ix_purchase_business_date ON purchase (business_id, purchase_date);

CREATE TABLE purchase_item (
    purchase_item_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id       BIGINT         NOT NULL REFERENCES purchase (purchase_id) ON DELETE CASCADE,
    ingredient_id     BIGINT         REFERENCES ingredient (ingredient_id),
    product_id        BIGINT         REFERENCES product (product_id),
    quantity          NUMERIC(14,3)  NOT NULL CHECK (quantity > 0),
    unit_code         VARCHAR(10)    NOT NULL REFERENCES unit_of_measure (unit_code),
    unit_cost         NUMERIC(14,4)  NOT NULL CHECK (unit_cost >= 0),
    line_total        NUMERIC(12,2)  NOT NULL CHECK (line_total >= 0),
    base_quantity     NUMERIC(14,3),
    landed_unit_cost  NUMERIC(14,4)  CHECK (landed_unit_cost >= 0),
    expiry_date       DATE,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_purchase_item_target CHECK (num_nonnulls(ingredient_id, product_id) = 1)
);

CREATE INDEX ix_purchase_item_ingredient ON purchase_item (ingredient_id);

-- Inventory ledger -----------------------------------------------------
CREATE TABLE inventory_movement (
    movement_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id           BIGINT         NOT NULL REFERENCES business (business_id),
    ingredient_id         BIGINT         REFERENCES ingredient (ingredient_id),
    product_id            BIGINT         REFERENCES product (product_id),
    movement_type         VARCHAR(25)    NOT NULL CHECK (movement_type IN (
                              'PURCHASE_RECEIPT', 'PRODUCTION_CONSUMPTION', 'PRODUCTION_OUTPUT',
                              'SALE', 'RETURN', 'WASTE', 'ADJUSTMENT', 'REVERSAL')),
    quantity_change       NUMERIC(14,3)  NOT NULL CHECK (quantity_change <> 0),
    unit_cost             NUMERIC(14,4)  NOT NULL CHECK (unit_cost >= 0),
    source_type           VARCHAR(30)    NOT NULL CHECK (source_type IN (
                              'PURCHASE_ITEM', 'PRODUCTION_CONSUMPTION', 'PRODUCTION_BATCH',
                              'SALE_ITEM', 'MANUAL')),
    source_id             BIGINT,
    reversed_movement_id  BIGINT         REFERENCES inventory_movement (movement_id),
    movement_date         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    reason                VARCHAR(300),
    created_by            BIGINT         NOT NULL REFERENCES app_user (user_id),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_movement_item CHECK (num_nonnulls(ingredient_id, product_id) = 1),
    CONSTRAINT ck_movement_sign CHECK (
        (movement_type IN ('PURCHASE_RECEIPT', 'PRODUCTION_OUTPUT') AND quantity_change > 0) OR
        (movement_type IN ('PRODUCTION_CONSUMPTION', 'SALE', 'WASTE') AND quantity_change < 0) OR
        (movement_type IN ('RETURN', 'ADJUSTMENT', 'REVERSAL'))),
    CONSTRAINT ck_movement_source CHECK (
        (source_type = 'MANUAL' AND source_id IS NULL) OR
        (source_type <> 'MANUAL' AND source_id IS NOT NULL)),
    CONSTRAINT ck_movement_reason CHECK (
        movement_type NOT IN ('ADJUSTMENT', 'WASTE', 'RETURN') OR reason IS NOT NULL),
    CONSTRAINT ck_movement_reversal CHECK (
        (movement_type = 'REVERSAL') = (reversed_movement_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_movement_source
    ON inventory_movement (movement_type, source_type, source_id)
    WHERE source_id IS NOT NULL;
CREATE INDEX ix_movement_ingredient ON inventory_movement (ingredient_id, movement_date);
CREATE INDEX ix_movement_product    ON inventory_movement (product_id, movement_date);

-- Production -----------------------------------------------------------
CREATE TABLE production_batch (
    batch_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id        BIGINT         NOT NULL REFERENCES business (business_id),
    product_id         BIGINT         NOT NULL REFERENCES product (product_id),
    recipe_version_id  BIGINT         NOT NULL REFERENCES recipe_version (recipe_version_id),
    planned_output     NUMERIC(14,3)  NOT NULL CHECK (planned_output > 0),
    actual_output      NUMERIC(14,3)  CHECK (actual_output > 0),
    production_date    DATE           NOT NULL,
    status             VARCHAR(10)    NOT NULL DEFAULT 'DRAFT'
                       CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    total_cost         NUMERIC(12,2)  CHECK (total_cost >= 0),
    unit_cost          NUMERIC(14,4)  CHECK (unit_cost >= 0),
    waste_notes        VARCHAR(300),
    produced_by        BIGINT         NOT NULL REFERENCES app_user (user_id),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_batch_confirmed CHECK (
        status <> 'CONFIRMED' OR
        (actual_output IS NOT NULL AND total_cost IS NOT NULL AND unit_cost IS NOT NULL))
);

CREATE TABLE production_consumption (
    consumption_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id            BIGINT         NOT NULL REFERENCES production_batch (batch_id),
    ingredient_id       BIGINT         NOT NULL REFERENCES ingredient (ingredient_id),
    quantity_base       NUMERIC(14,3)  NOT NULL CHECK (quantity_base > 0),
    unit_cost_snapshot  NUMERIC(14,4)  NOT NULL CHECK (unit_cost_snapshot >= 0),
    line_cost           NUMERIC(14,4)  NOT NULL CHECK (line_cost >= 0),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_consumption UNIQUE (batch_id, ingredient_id)
);

-- Sales ----------------------------------------------------------------
CREATE TABLE customer (
    customer_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id  BIGINT        NOT NULL REFERENCES business (business_id),
    name         VARCHAR(120)  NOT NULL,
    email        VARCHAR(255),
    phone        VARCHAR(30),
    notes        VARCHAR(500),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE sale (
    sale_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id     BIGINT         NOT NULL REFERENCES business (business_id),
    customer_id     BIGINT         REFERENCES customer (customer_id),
    sale_number     VARCHAR(30)    NOT NULL,
    sale_date       TIMESTAMPTZ    NOT NULL,
    status          VARCHAR(10)    NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOIDED')),
    payment_method  VARCHAR(15)
                    CHECK (payment_method IN ('CASH', 'CARD', 'EFT', 'MOBILE', 'OTHER')),
    currency_code   CHAR(3)        NOT NULL,
    subtotal        NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    discount_total  NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (discount_total >= 0),
    tax_total       NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (tax_total >= 0),
    total           NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (total >= 0),
    notes           VARCHAR(500),
    recorded_by     BIGINT         NOT NULL REFERENCES app_user (user_id),
    voided_by       BIGINT         REFERENCES app_user (user_id),
    voided_at       TIMESTAMPTZ,
    void_reason     VARCHAR(300),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_sale_number  UNIQUE (business_id, sale_number),
    CONSTRAINT ck_sale_total   CHECK (total = subtotal - discount_total + tax_total),
    CONSTRAINT ck_sale_payment CHECK (status = 'DRAFT' OR payment_method IS NOT NULL),
    CONSTRAINT ck_sale_void    CHECK (status <> 'VOIDED' OR
        (voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL))
);

CREATE INDEX ix_sale_business_date ON sale (business_id, status, sale_date);

CREATE TABLE sale_item (
    sale_item_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id             BIGINT         NOT NULL REFERENCES sale (sale_id) ON DELETE CASCADE,
    product_id          BIGINT         NOT NULL REFERENCES product (product_id),
    quantity            NUMERIC(14,3)  NOT NULL CHECK (quantity > 0),
    unit_price          NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
    discount_amount     NUMERIC(12,2)  NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    tax_rate            NUMERIC(5,4)   NOT NULL CHECK (tax_rate >= 0 AND tax_rate < 1),
    net_amount          NUMERIC(12,2)  NOT NULL CHECK (net_amount >= 0),
    tax_amount          NUMERIC(12,2)  NOT NULL CHECK (tax_amount >= 0),
    line_total          NUMERIC(12,2)  NOT NULL CHECK (line_total >= 0),
    unit_cost_snapshot  NUMERIC(14,4)  CHECK (unit_cost_snapshot >= 0),
    line_cost           NUMERIC(14,4)  CHECK (line_cost >= 0),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_sale_item_discount CHECK (discount_amount <= round(quantity * unit_price, 2)),
    CONSTRAINT ck_sale_item_total    CHECK (line_total = net_amount + tax_amount)
);

CREATE INDEX ix_sale_item_product ON sale_item (product_id);

-- Expenses -------------------------------------------------------------
CREATE TABLE expense_category (
    expense_category_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id          BIGINT       NOT NULL REFERENCES business (business_id),
    name                 VARCHAR(60)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_expense_category UNIQUE (business_id, name)
);

CREATE TABLE expense (
    expense_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id          BIGINT         NOT NULL REFERENCES business (business_id),
    expense_category_id  BIGINT         NOT NULL REFERENCES expense_category (expense_category_id),
    supplier_id          BIGINT         REFERENCES supplier (supplier_id),
    description          VARCHAR(200)   NOT NULL,
    amount               NUMERIC(12,2)  NOT NULL CHECK (amount > 0),
    currency_code        CHAR(3)        NOT NULL,
    expense_date         DATE           NOT NULL,
    payment_method       VARCHAR(15)
                         CHECK (payment_method IN ('CASH', 'CARD', 'EFT', 'MOBILE', 'OTHER')),
    reference_no         VARCHAR(50),
    notes                TEXT,
    recorded_by          BIGINT         NOT NULL REFERENCES app_user (user_id),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX ix_expense_business_date ON expense (business_id, expense_date);

-- Forecasting ----------------------------------------------------------
CREATE TABLE model_evaluation (
    evaluation_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id     BIGINT         NOT NULL REFERENCES business (business_id),
    product_id      BIGINT         REFERENCES product (product_id),
    forecast_type   VARCHAR(12)    NOT NULL CHECK (forecast_type IN ('DEMAND', 'STOCKOUT')),
    model_name      VARCHAR(60)    NOT NULL,
    model_version   VARCHAR(20)    NOT NULL,
    train_start     DATE           NOT NULL,
    train_end       DATE           NOT NULL,
    test_start      DATE           NOT NULL,
    test_end        DATE           NOT NULL,
    sample_days     INTEGER        NOT NULL CHECK (sample_days > 0),
    mae             NUMERIC(14,4)  NOT NULL CHECK (mae >= 0),
    rmse            NUMERIC(14,4)  NOT NULL CHECK (rmse >= 0),
    baseline_mae    NUMERIC(14,4)  NOT NULL CHECK (baseline_mae >= 0),
    baseline_rmse   NUMERIC(14,4)  NOT NULL CHECK (baseline_rmse >= 0),
    selected_model  VARCHAR(60)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_evaluation_periods CHECK (train_start <= train_end AND train_end < test_start
                                            AND test_start <= test_end)
);

CREATE TABLE forecast (
    forecast_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id          BIGINT         NOT NULL REFERENCES business (business_id),
    forecast_type        VARCHAR(12)    NOT NULL CHECK (forecast_type IN ('DEMAND', 'STOCKOUT')),
    product_id           BIGINT         REFERENCES product (product_id),
    ingredient_id        BIGINT         REFERENCES ingredient (ingredient_id),
    model_evaluation_id  BIGINT         REFERENCES model_evaluation (evaluation_id),
    model_name           VARCHAR(60)    NOT NULL,
    model_version        VARCHAR(20)    NOT NULL,
    training_cutoff      DATE           NOT NULL,
    target_date          DATE           NOT NULL,
    horizon_days         INTEGER        NOT NULL CHECK (horizon_days > 0),
    predicted_value      NUMERIC(14,3)  CHECK (predicted_value >= 0),
    value_unit           VARCHAR(10)    NOT NULL,
    actual_value         NUMERIC(14,3),
    status               VARCHAR(20)    NOT NULL
                         CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'INSUFFICIENT_DATA')),
    generated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_forecast_target CHECK (
        (forecast_type = 'DEMAND'   AND product_id IS NOT NULL AND ingredient_id IS NULL) OR
        (forecast_type = 'STOCKOUT' AND ingredient_id IS NOT NULL AND product_id IS NULL)),
    CONSTRAINT ck_forecast_value CHECK (status = 'INSUFFICIENT_DATA' OR predicted_value IS NOT NULL)
);

CREATE INDEX ix_forecast_lookup ON forecast (business_id, forecast_type, target_date);

-- Alerts ---------------------------------------------------------------
CREATE TABLE notification (
    notification_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id        BIGINT        NOT NULL REFERENCES business (business_id),
    notification_type  VARCHAR(30)   NOT NULL CHECK (notification_type IN (
                           'LOW_STOCK', 'EXPIRING_SOON', 'EXPIRED', 'COST_INCREASE',
                           'PREDICTED_STOCKOUT', 'FORECAST_CHANGE', 'ADMIN')),
    nature             VARCHAR(10)   NOT NULL CHECK (nature IN ('OBSERVED', 'PREDICTED')),
    message            VARCHAR(300)  NOT NULL,
    related_type       VARCHAR(20),
    related_id         BIGINT,
    status             VARCHAR(10)   NOT NULL DEFAULT 'OPEN'
                       CHECK (status IN ('OPEN', 'READ', 'RESOLVED')),
    resolved_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notification_open
    ON notification (business_id, notification_type, related_type, related_id)
    WHERE status <> 'RESOLVED';
