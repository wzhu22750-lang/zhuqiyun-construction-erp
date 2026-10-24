CREATE TABLE import_batches (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 1,
 entity_type VARCHAR(30) NOT NULL, file_hash VARCHAR(64) NOT NULL, row_count INT NOT NULL,
 operator_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(tenant_id,entity_type,file_hash), FOREIGN KEY(operator_id) REFERENCES users(id)
);
