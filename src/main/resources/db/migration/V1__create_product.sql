CREATE TABLE product (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_product_sku UNIQUE (sku),
    CONSTRAINT uq_product_name UNIQUE (name),
    CONSTRAINT ck_product_sku CHECK (length(btrim(sku)) > 0 AND sku = btrim(sku)),
    CONSTRAINT ck_product_name CHECK (length(btrim(name)) > 0 AND name = btrim(name))
);

CREATE TABLE warehouse (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_warehouse_code CHECK (length(btrim(code)) > 0 AND code = btrim(code)),
    CONSTRAINT ck_warehouse_name CHECK (length(btrim(name)) > 0 AND name = btrim(name))
);
INSERT INTO warehouse (code, name) VALUES ('DEFAULT', '기본 창고');

CREATE TABLE inventory (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id),
    warehouse_id BIGINT NOT NULL REFERENCES warehouse(id),
    quantity BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT ck_inventory_quantity CHECK (quantity >= 0)
);
CREATE INDEX ix_inventory_warehouse ON inventory (warehouse_id);

CREATE TABLE stock_movement (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inventory_id BIGINT NOT NULL REFERENCES inventory(id),
    type VARCHAR(20) NOT NULL,
    quantity_delta BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stock_movement_type CHECK (type IN ('RECEIPT', 'SHIPMENT')),
    CONSTRAINT ck_stock_movement_delta CHECK (
        (type = 'RECEIPT' AND quantity_delta > 0)
        OR (type = 'SHIPMENT' AND quantity_delta < 0)
    )
);
CREATE INDEX ix_stock_movement_inventory_time ON stock_movement (inventory_id, created_at, id);

COMMENT ON TABLE product IS '상품 마스터';
COMMENT ON COLUMN product.sku IS '상품 식별 코드';
COMMENT ON COLUMN product.name IS '앞뒤 공백을 제거한 상품명. 기존 API의 상품 식별 기준이며 대소문자를 구분한다.';
COMMENT ON TABLE warehouse IS '창고 마스터';
COMMENT ON TABLE inventory IS '상품 및 창고별 현재 재고. 입출고 잠금 대상';
COMMENT ON COLUMN inventory.quantity IS '현재 재고. 0 이상 BIGINT 최댓값 이하.';
COMMENT ON TABLE stock_movement IS '입출고 이력. 변경량의 합계가 현재 재고';
