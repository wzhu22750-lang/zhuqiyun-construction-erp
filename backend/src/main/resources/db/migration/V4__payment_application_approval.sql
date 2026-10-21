-- Existing PAID applications remain historical records; existing PENDING ones require approval.
ALTER TABLE payment_applications ADD COLUMN reviewer_id BIGINT;
ALTER TABLE payment_applications ADD COLUMN reviewed_at TIMESTAMP NULL;
ALTER TABLE payment_applications ADD COLUMN review_comment VARCHAR(1000);
CREATE INDEX idx_payment_application_reservation ON payment_applications(payable_id,status);
CREATE TABLE payment_application_records (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL DEFAULT 1,
 application_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL,
 decision VARCHAR(30) NOT NULL,
 comment VARCHAR(1000),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(application_id) REFERENCES payment_applications(id),
 FOREIGN KEY(operator_id) REFERENCES users(id)
);
-- Preserve customized permissions; add approval only to the existing boss finance reader.
UPDATE roles SET permissions=REPLACE(permissions,'"finance:read"','"finance:read","finance:approve"')
 WHERE code='boss' AND permissions LIKE '%"finance:read"%' AND permissions NOT LIKE '%"finance:approve"%';
