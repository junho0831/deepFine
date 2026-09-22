CREATE TABLE product (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT uq_product_name UNIQUE (name),
    CONSTRAINT ck_product_name CHECK (length(btrim(name)) > 0 AND name = btrim(name)),
    CONSTRAINT ck_product_quantity CHECK (quantity >= 0)
);

COMMENT ON TABLE product IS '현재 상품 재고';
COMMENT ON COLUMN product.name IS '앞뒤 공백을 제거한 상품명. MVP의 상품 식별 기준이며 대소문자를 구분한다.';
COMMENT ON COLUMN product.quantity IS '현재 재고. 0 이상 BIGINT 최댓값 이하.';
