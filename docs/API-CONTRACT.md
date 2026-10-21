# 筑企云内部接口契约
所有路径前缀 /api；JSON 响应 {code:0,data:...,msg:""}，错误 HTTP400/403/409 + {code,msg}。Authorization: Bearer <token>。数据字段使用数据库 snake_case。金额是十进制字符串，前端仅格式化展示，计算以服务端为准。GET 列表直接 data 数组。所有写操作记录审计并事务执行。

- POST /auth/login {username,password} -> {token,user:{id,username,name,role,permissions:[],menus:[]}}
- GET /auth/me -> user；POST /auth/logout
- GET /bootstrap -> {user,company:{name,system_name,theme_color},projects:[],materials:[],suppliers:[],warehouses:[],users:[],dictionaries:{...}}
- GET /dashboard -> {metrics:{active_projects,contract_amount,completed_value,budget_amount,actual_cost,purchase_amount,pending_requests,outstanding_amount},budget_vs_cost:[{name,budget,cost}],monthly_purchases:[{month,amount}],cost_composition:[{name,value}],project_progress:[{name,progress}],order_status:[{name,value}],todos:[],recent_activity:[]}
- GET /projects；GET /projects/{id} -> {project,metrics:{request_amount,order_amount,receipt_amount,material_cost,paid_amount,other_cost,actual_cost,budget_usage,estimated_profit},costs:[],requests:[],orders:[],contracts:[],progress:[]}
- POST /projects；PUT /projects/{id}；DELETE /projects/{id} (only if no linked documents)
  fields: code,name,owner,contractor,manager_id,location,type,contract_amount,budget_amount,start_date,end_date,progress,status (ACTIVE/PLANNING/COMPLETED/SUSPENDED)
- GET /suppliers；GET /suppliers/{id} -> {supplier,quotes,orders,metrics}；POST /suppliers；PUT /suppliers/{id}；DELETE /suppliers/{id} (unused only)
  fields: code,name,contact,phone,category,status(ACTIVE/INACTIVE),qualification,qualification_expiry,rating
- GET /materials；POST /materials；PUT /materials/{id}；DELETE /materials/{id} (unused only)
  fields: code,name,specification,unit,category,safety_stock,reference_price
- GET /warehouses；POST /warehouses；PUT /warehouses/{id} fields code,name,location
- GET /requests -> joined project_name,applicant_name,items[]；GET /requests/{id} -> request row+items[],quotes[],approvals[]
- POST /requests；PUT /requests/{id} {project_id,required_date,note,items:[{material_id,quantity,unit_price}]} -> request detail
- POST /requests/{id}/submit {}；POST /requests/{id}/approve {decision:APPROVED/REJECTED,comment}
- GET /requests/{id}/quotes；POST /requests/{id}/quotes {supplier_id,delivery_days,valid_until,note,items:[{request_item_id,unit_price}]} -> quote with items[]
- POST /requests/{id}/orders {quote_id,reason} -> order detail. One whole-request award; unique request per order; no overorder. Requires APPROVED. Quotes immutable after award.
- GET /orders；GET /orders/{id} -> row + items[{id,material_id,quantity,received_quantity,unit_price,amount,material_name,specification,unit}],receipts[],payments[]
- POST /orders/{id}/receive {warehouse_id,reference,note,items:[{order_item_id,quantity}]} -> receipt (inspect+post receipt in one atomic operation), updates average stock and creates payable for actual accepted amount.
- POST /orders/{id}/cancel {reason} (only unreceived); states OPEN/PARTIAL/COMPLETED/CANCELLED
- GET /inventory -> rows [{warehouse_id,warehouse_name,material_id,material_name,specification,unit,quantity,average_price,stock_value,safety_stock}]
- GET /stock-movements -> rows including project_name,material_name,warehouse_name,kind,quantity,unit_cost,amount,reference,operator_name,created_at
- POST /issues {project_id,warehouse_id,reference,note,items:[{material_id,quantity}]} -> issue with cost, disallows insufficient stock. kind RECEIPT/ISSUE; issue is project material cost; receipt is inventory, not project consumption.
- GET /costs -> project joined metrics；POST /costs {project_id,category:LABOR/MACHINE/SUBCONTRACT/OTHER,amount,description,incurred_date}
- GET /contracts；POST /contracts；PUT /contracts/{id}；DELETE /contracts/{id} (unused only) fields code,name,type(MAIN/PURCHASE/SUBCONTRACT),project_id,supplier_id(nullable),order_id(nullable),amount,signed_date,start_date,end_date,status(ACTIVE/COMPLETED/DRAFT),payment_plan(JSON text),attachment_name(optional),attachment_url(optional). Attachments authenticated upload through POST /contracts/{id}/attachment multipart file, GET /contracts/{id}/attachment.
- GET /payables -> rows order_id,order_code,project_name,supplier_name,amount,paid_amount,outstanding_amount,reserved_amount,available_amount
- GET /payments (`finance:read`)；POST /payments {payable_id,amount,reference,note} -> payment. Direct bookkeeping requires `finance:record` (default admin only). Unique reference <=100 characters; locks payable, respects reservations and contract cap.
- GET /payment-applications (`finance:read`) -> joined project_name,supplier_name,order_code,applicant_name,reviewer_name,reviewer_id,reviewed_at,review_comment,payment_id,status.
- POST /payment-applications {payable_id,amount,note} (`finance:write`) -> PENDING application. PENDING and APPROVED applications both reserve amount; actual received balance and linked contract cap apply.
- POST /payment-applications/{id}/review {decision:APPROVED/REJECTED,comment} (`finance:approve`); only PENDING, applicant must differ from reviewer; rejection requires comment.
- POST /payment-applications/{id}/cancel {reason} (`finance:write`); applicant or wildcard admin can withdraw PENDING/APPROVED applications, releases reservation, retains history.
- POST /payment-applications/{id}/pay {reference,note} (`finance:write`); only APPROVED, reviewer must differ from executor, amount fixed to approved application; rechecks current balance, other reservations, contract cap and reference uniqueness. Posts cash and PAID state atomically.
- GET /payment-applications/{id}/records (`finance:read`) -> [{id,application_id,operator_id,operator_name,decision,comment,created_at}]; project scope is checked independently. Decisions SUBMITTED/APPROVED/REJECTED/CANCELLED/PAID. Pre-V4 applications may have empty history.
- GET /approvals (pending requests)；GET /approval-records
- GET /audit-logs；GET /admin/users；POST /admin/users；PUT /admin/users/{id}; GET /admin/roles；PUT /admin/roles/{id} {permissions:[],menus:[]}; PUT /admin/company {name,system_name,theme_color}; GET /admin/dictionaries；PUT /admin/dictionaries/{id} {label,value,sort_order}
Roles admin,boss,manager,buyer,warehouse,finance. Each user one role. Manager only assigned projects and linked documents. Others company-scope. All reads scoped by tenant_id=1. Tenant column is expansion seam; no claim of complete SaaS isolation.
Permissions project:read/write,master:read/write,request:read/write/approve,quote:write,order:read/write/receive,inventory:read/issue,cost:read/write,contract:read/write,finance:read/write/approve/record,dashboard:read,system:manage. Admin wildcard *. Manager request write only self, never own approve; boss approves procurement and payment requests; buyer quotes and orders; warehouse receive/issue; finance applies and executes approved payments. `finance:record` is a separate direct bookkeeping permission and is not granted to the default finance role.
