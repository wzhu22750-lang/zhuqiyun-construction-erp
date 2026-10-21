ALTER TABLE purchase_orders ADD COLUMN closed_amount DECIMAL(20,2) NOT NULL DEFAULT 0;
ALTER TABLE purchase_orders ADD COLUMN close_reason VARCHAR(1000);
ALTER TABLE purchase_orders ADD COLUMN closed_by BIGINT;
ALTER TABLE purchase_orders ADD COLUMN closed_at TIMESTAMP NULL;
ALTER TABLE purchase_orders ADD COLUMN replacement_request_id BIGINT;
ALTER TABLE purchase_requests ADD COLUMN source_order_id BIGINT;
ALTER TABLE order_items ADD COLUMN closed_quantity DECIMAL(18,3) NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN closed_amount DECIMAL(20,2) NOT NULL DEFAULT 0;
CREATE TABLE issue_returns (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1,
 code VARCHAR(60) NOT NULL UNIQUE, issue_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 warehouse_id BIGINT NOT NULL, reference VARCHAR(100) NOT NULL UNIQUE, reason VARCHAR(1000) NOT NULL,
 amount DECIMAL(20,2) NOT NULL, operator_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(issue_id) REFERENCES issues(id), FOREIGN KEY(project_id) REFERENCES projects(id), FOREIGN KEY(warehouse_id) REFERENCES warehouses(id)
);
CREATE TABLE issue_return_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1,
 return_id BIGINT NOT NULL, source_movement_id BIGINT NOT NULL, quantity DECIMAL(18,3) NOT NULL, amount DECIMAL(20,2) NOT NULL,
 UNIQUE(return_id,source_movement_id), FOREIGN KEY(return_id) REFERENCES issue_returns(id), FOREIGN KEY(source_movement_id) REFERENCES stock_movements(id)
);
CREATE INDEX idx_issue_return_source ON issue_return_items(source_movement_id);
CREATE TABLE purchase_returns (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1,
 code VARCHAR(60) NOT NULL UNIQUE, receipt_id BIGINT NOT NULL, order_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 warehouse_id BIGINT NOT NULL, reference VARCHAR(100) NOT NULL UNIQUE, reason VARCHAR(1000) NOT NULL,
 amount DECIMAL(20,2) NOT NULL, stock_amount DECIMAL(20,2) NOT NULL, variance_amount DECIMAL(20,2) NOT NULL,
 cost_id BIGINT, operator_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(receipt_id) REFERENCES receipts(id), FOREIGN KEY(order_id) REFERENCES purchase_orders(id),
 FOREIGN KEY(project_id) REFERENCES projects(id), FOREIGN KEY(warehouse_id) REFERENCES warehouses(id), FOREIGN KEY(cost_id) REFERENCES costs(id)
);
CREATE TABLE purchase_return_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1,
 return_id BIGINT NOT NULL, receipt_item_id BIGINT NOT NULL, quantity DECIMAL(18,3) NOT NULL,
 amount DECIMAL(20,2) NOT NULL, stock_amount DECIMAL(20,2) NOT NULL,
 UNIQUE(return_id,receipt_item_id), FOREIGN KEY(return_id) REFERENCES purchase_returns(id), FOREIGN KEY(receipt_item_id) REFERENCES receipt_items(id)
);
CREATE INDEX idx_purchase_return_source ON purchase_return_items(receipt_item_id);
ALTER TABLE stock_movements ADD COLUMN issue_return_id BIGINT;
ALTER TABLE stock_movements ADD COLUMN purchase_return_id BIGINT;
CREATE TABLE supplier_refunds (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, payable_id BIGINT NOT NULL,
 amount DECIMAL(20,2) NOT NULL, reference VARCHAR(100) NOT NULL UNIQUE, note VARCHAR(1000),
 operator_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(payable_id) REFERENCES payables(id), FOREIGN KEY(operator_id) REFERENCES users(id)
);
CREATE INDEX idx_supplier_refund_payable ON supplier_refunds(payable_id);
