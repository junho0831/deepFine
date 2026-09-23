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

CREATE TABLE idempotency_request (
    request_key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    response_product_id BIGINT,
    response_name VARCHAR(100),
    response_quantity BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_idempotency_key CHECK (request_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
    CONSTRAINT ck_idempotency_hash CHECK (request_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_idempotency_response CHECK (
        (response_product_id IS NULL AND response_name IS NULL AND response_quantity IS NULL)
        OR (response_product_id IS NOT NULL AND response_name IS NOT NULL AND response_quantity IS NOT NULL
            AND response_product_id > 0 AND response_quantity >= 0)
    )
);
COMMENT ON TABLE idempotency_request IS '입출고 중복 방지 키와 최초 성공 응답. 재고 변경과 같은 트랜잭션에 저장';
