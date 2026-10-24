ALTER TABLE stock_movements ADD COLUMN source_order_item_id BIGINT;
CREATE INDEX idx_stock_source_order_item ON stock_movements(source_order_item_id,warehouse_id,kind);
