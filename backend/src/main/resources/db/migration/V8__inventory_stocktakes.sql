ALTER TABLE inventory ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stock_movements MODIFY COLUMN project_id BIGINT NULL;
ALTER TABLE stock_movements ADD COLUMN stocktake_id BIGINT;
ALTER TABLE stock_movements ADD COLUMN stocktake_item_id BIGINT;
CREATE UNIQUE INDEX uq_stocktake_movement ON stock_movements(stocktake_item_id);
CREATE TABLE stocktakes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, code VARCHAR(60) NOT NULL UNIQUE,
 warehouse_id BIGINT NOT NULL, applicant_id BIGINT NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
 note VARCHAR(1000), correction_of BIGINT, reviewer_id BIGINT, reviewed_at TIMESTAMP NULL,
 review_comment VARCHAR(1000), submitted_at TIMESTAMP NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(warehouse_id) REFERENCES warehouses(id),
 FOREIGN KEY(applicant_id) REFERENCES users(id), FOREIGN KEY(correction_of) REFERENCES stocktakes(id)
);
CREATE TABLE stocktake_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, stocktake_id BIGINT NOT NULL,
 material_id BIGINT NOT NULL, snapshot_quantity DECIMAL(18,3) NOT NULL, snapshot_stock_value DECIMAL(20,2) NOT NULL,
 snapshot_average_price DECIMAL(18,6) NOT NULL, snapshot_revision BIGINT NOT NULL,
 counted_quantity DECIMAL(18,3) NOT NULL, difference_quantity DECIMAL(18,3) NOT NULL,
 gain_unit_price DECIMAL(18,6), adjustment_amount DECIMAL(20,2), reason VARCHAR(1000) NOT NULL,
 UNIQUE(stocktake_id,material_id), FOREIGN KEY(stocktake_id) REFERENCES stocktakes(id), FOREIGN KEY(material_id) REFERENCES materials(id)
);
CREATE TABLE stocktake_records (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, stocktake_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL, decision VARCHAR(30) NOT NULL, comment VARCHAR(1000), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(stocktake_id) REFERENCES stocktakes(id), FOREIGN KEY(operator_id) REFERENCES users(id)
);
CREATE INDEX idx_stocktakes_queue ON stocktakes(tenant_id,status,applicant_id);
UPDATE roles SET permissions=REPLACE(permissions,'"inventory:issue"','"inventory:issue","stocktake:read","stocktake:write"') WHERE code='warehouse' AND permissions LIKE '%"inventory:issue"%' AND permissions NOT LIKE '%"stocktake:read"%';
UPDATE roles SET permissions=REPLACE(permissions,'"inventory:read"','"inventory:read","stocktake:read","stocktake:approve"') WHERE code='boss' AND permissions LIKE '%"inventory:read"%' AND permissions NOT LIKE '%"stocktake:approve"%';
