-- Read-only reconciliation. Every violations count must be zero.
SELECT 'request_line_totals' check_name, COUNT(*) violations FROM purchase_requests h WHERE h.total_amount <> COALESCE((SELECT SUM(d.amount) FROM request_items d WHERE d.request_id=h.id),0)
UNION ALL SELECT 'quote_line_totals', COUNT(*) FROM quotes h WHERE h.total_amount <> COALESCE((SELECT SUM(d.amount) FROM quote_items d WHERE d.quote_id=h.id),0)
UNION ALL SELECT 'order_line_totals', COUNT(*) FROM purchase_orders h WHERE h.total_amount <> COALESCE((SELECT SUM(d.amount) FROM order_items d WHERE d.order_id=h.id),0)
UNION ALL SELECT 'receipt_line_totals', COUNT(*) FROM receipts h WHERE h.amount <> COALESCE((SELECT SUM(d.amount) FROM receipt_items d WHERE d.receipt_id=h.id),0)
UNION ALL SELECT 'receipt_order_quantity', COUNT(*) FROM order_items d WHERE d.received_quantity <> COALESCE((SELECT SUM(r.quantity) FROM receipt_items r WHERE r.order_item_id=d.id),0) OR d.received_quantity>d.quantity OR d.received_quantity<0
UNION ALL SELECT 'order_requires_approval', COUNT(*) FROM purchase_orders o JOIN purchase_requests r ON r.id=o.request_id WHERE r.status<>'APPROVED'
UNION ALL SELECT 'order_request_quantity', COUNT(*) FROM request_items r WHERE COALESCE((SELECT SUM(o.quantity) FROM order_items o WHERE o.request_item_id=r.id),0)>r.quantity
UNION ALL SELECT 'stock_quantity_flow', COUNT(*) FROM inventory i WHERE i.quantity <> COALESCE((SELECT SUM(m.quantity) FROM stock_movements m WHERE m.tenant_id=i.tenant_id AND m.warehouse_id=i.warehouse_id AND m.material_id=i.material_id),0)
UNION ALL SELECT 'stock_value_flow', COUNT(*) FROM inventory i WHERE i.stock_value <> COALESCE((SELECT SUM(CASE WHEN m.kind='RECEIPT' THEN m.amount ELSE -m.amount END) FROM stock_movements m WHERE m.tenant_id=i.tenant_id AND m.warehouse_id=i.warehouse_id AND m.material_id=i.material_id),0)
UNION ALL SELECT 'stock_nonnegative', COUNT(*) FROM inventory i WHERE i.quantity<0 OR i.stock_value<0 OR (i.quantity=0 AND i.stock_value<>0)
UNION ALL SELECT 'movement_has_inventory', COUNT(*) FROM stock_movements m LEFT JOIN inventory i ON i.tenant_id=m.tenant_id AND i.warehouse_id=m.warehouse_id AND i.material_id=m.material_id WHERE i.id IS NULL
UNION ALL SELECT 'issue_cost_flow', COUNT(*) FROM issues h WHERE h.amount <> COALESCE((SELECT SUM(m.amount) FROM stock_movements m WHERE m.issue_id=h.id AND m.kind='ISSUE'),0)
UNION ALL SELECT 'payable_receipt_total', COUNT(*) FROM payables p WHERE p.amount <> COALESCE((SELECT SUM(r.amount) FROM receipts r WHERE r.order_id=p.order_id),0)
UNION ALL SELECT 'paid_payment_total', COUNT(*) FROM payables p WHERE p.paid_amount <> COALESCE((SELECT SUM(t.amount) FROM payments t WHERE t.payable_id=p.id),0) OR p.paid_amount>p.amount
UNION ALL SELECT 'receipt_has_payable', COUNT(*) FROM receipts r LEFT JOIN payables p ON p.order_id=r.order_id WHERE p.id IS NULL
UNION ALL SELECT 'payment_application_link', COUNT(*) FROM payment_applications a LEFT JOIN payments p ON p.id=a.payment_id WHERE a.status='PAID' AND (p.id IS NULL OR p.amount<>a.amount OR p.payable_id<>a.payable_id)
UNION ALL SELECT 'payment_application_reservations', COUNT(*) FROM payables p WHERE p.paid_amount+COALESCE((SELECT SUM(a.amount) FROM payment_applications a WHERE a.payable_id=p.id AND a.status IN ('PENDING','APPROVED')),0)>p.amount
UNION ALL SELECT 'payment_application_approved_reviewer', COUNT(*) FROM payment_applications a WHERE a.status='APPROVED' AND (a.reviewer_id IS NULL OR a.reviewer_id=a.applicant_id OR a.reviewed_at IS NULL)
UNION ALL SELECT 'payment_application_execution_separation', COUNT(*) FROM payment_applications a JOIN payments p ON p.id=a.payment_id WHERE a.reviewer_id IS NOT NULL AND a.status='PAID' AND a.reviewer_id=p.operator_id
UNION ALL SELECT 'payment_application_terminal_link', COUNT(*) FROM payment_applications a WHERE a.status<>'PAID' AND a.payment_id IS NOT NULL;
