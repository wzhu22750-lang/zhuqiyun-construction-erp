ALTER TABLE contracts ADD COLUMN plan_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE contracts ADD COLUMN plan_migrated BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE contract_payment_stages (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, contract_id BIGINT NOT NULL,
 stage_name VARCHAR(120) NOT NULL, due_date DATE, amount DECIMAL(20,2) NOT NULL,
 note VARCHAR(1000), sort_order INT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(contract_id,sort_order), FOREIGN KEY(contract_id) REFERENCES contracts(id)
);
CREATE TABLE contract_payment_allocations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, contract_id BIGINT NOT NULL,
 stage_id BIGINT NOT NULL, payment_id BIGINT, refund_id BIGINT, amount DECIMAL(20,2) NOT NULL,
 reference VARCHAR(100) NOT NULL UNIQUE, operator_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 voided BOOLEAN NOT NULL DEFAULT FALSE, voided_by BIGINT, voided_at TIMESTAMP NULL, void_reason VARCHAR(1000),
 FOREIGN KEY(contract_id) REFERENCES contracts(id), FOREIGN KEY(stage_id) REFERENCES contract_payment_stages(id),
 FOREIGN KEY(payment_id) REFERENCES payments(id), FOREIGN KEY(refund_id) REFERENCES supplier_refunds(id)
);
CREATE TABLE contract_plan_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1, contract_id BIGINT NOT NULL,
 before_json TEXT, after_json TEXT NOT NULL, operator_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(contract_id) REFERENCES contracts(id)
);
CREATE INDEX idx_plan_due ON contract_payment_stages(due_date,contract_id);
CREATE INDEX idx_allocation_payment ON contract_payment_allocations(payment_id,voided);
CREATE INDEX idx_allocation_refund ON contract_payment_allocations(refund_id,voided);
