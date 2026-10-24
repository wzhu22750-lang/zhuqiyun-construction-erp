# 筑企云内部接口契约
所有路径前缀 /api；JSON 响应 {code:0,data:...,msg:""}，错误 HTTP400/403/409 + {code,msg}。Authorization: Bearer <token>。数据字段使用数据库 snake_case。金额是十进制字符串，前端仅格式化展示，计算以服务端为准。旧 GET 列表为 data 数组；本轮页面使用 GET /records/{entity} 的 {items,total,page,size}，盘点和时间线亦分页。所有写操作记录审计并事务执行。

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
- POST /orders/{id}/cancel {reason} (only unreceived); states OPEN/PARTIAL/COMPLETED/CANCELLED/CLOSED
- GET /inventory -> rows [{warehouse_id,warehouse_name,material_id,material_name,specification,unit,quantity,average_price,stock_value,safety_stock}]
- GET /stock-movements -> rows including project_name,material_name,warehouse_name,kind,quantity,unit_cost,amount,reference,operator_name,created_at
- POST /issues {project_id,warehouse_id,reference,note,items:[{material_id,quantity}]} -> issue with cost, disallows insufficient stock. kind RECEIPT/ISSUE; issue is project material cost; receipt is inventory, not project consumption.
- GET /costs -> project joined metrics；POST /costs {project_id,category:LABOR/MACHINE/SUBCONTRACT/OTHER,amount,description,incurred_date}
- GET /contracts；POST /contracts；PUT /contracts/{id}；DELETE /contracts/{id} (unused only) fields code,name,type(MAIN/PURCHASE/SUBCONTRACT),project_id,supplier_id(nullable),order_id(nullable),amount,signed_date,start_date,end_date,status(ACTIVE/COMPLETED/DRAFT),payment_plan(legacy JSON text; current UI uses payment-plan API),attachment_name(optional),attachment_url(optional). Attachments authenticated upload through POST /contracts/{id}/attachment multipart file, GET /contracts/{id}/attachment.
- GET /payables -> rows order_id,order_code,project_name,supplier_name,amount,paid_amount,outstanding_amount,reserved_amount,available_amount,refunded_amount,refundable_amount
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
Permissions quote:read,inventory:company,stocktake:read/write/approve,contract:plan,project:read/write,master:read/write,request:read/write/approve,quote:write,order:read/write/receive,inventory:read/issue,cost:read/write,contract:read/write,finance:read/write/approve/record,dashboard:read,system:manage. Admin wildcard *. Manager request write only self, never own approve; boss approves procurement and payment requests; buyer quotes and orders; warehouse receive/issue; finance applies and executes approved payments. `finance:record` is a separate direct bookkeeping permission and is not granted to the default finance role.

## 采购纠错与履约（V5）

- POST /orders/{id}/close {reason}：`order:write`，只关闭 PARTIAL 订单未到货部分。新增 closed_amount、close_reason、closed_by、closed_at；order_items 新增 closed_quantity、closed_amount。原订单数量与金额、原收货保持不变。
- POST /orders/{id}/reorder-request {required_date,note}：`request:write`，仅 CLOSED/CANCELLED，每单生成一次 DRAFT，数量取未收部分。新申请 source_order_id/source_order_code，原订单 replacement_request_id；需重新审批。
- GET /orders/{id} 新增 effective_amount（原金额减关闭、退货）、return_amount、returns[]、refunds[]、payable、events[]；receipt.items[] 包含 id、returned_quantity、returnable_quantity；order.items[] 包含 remaining_quantity、returned_quantity。
- GET /issues：`inventory:read`，领料头及 items[]，item.id 为原 ISSUE 库存流水编号，含 material_name、specification、unit、原 quantity/amount、returned_quantity、returnable_quantity。
- GET /issue-returns：`inventory:read`，含原 issue_code、项目、仓库、金额、凭证、原因、人员和时间。
- POST /issues/{id}/returns {reference,reason,items:[{source_id,quantity}]}：`inventory:issue`，source_id 为该领料单原 ISSUE 流水；按原领料成本分次退回原仓，最后一笔吸收尾差。
- GET /purchase-returns：`order:read`，含原订单、收货、应付冲减 amount、库存冲减 stock_amount、计价差额 variance_amount、原因及操作人。
- POST /receipts/{id}/returns {reference,reason,items:[{source_id,quantity}]}：`order:receive`，source_id 为该入库单 receipt_items.id；检查原可退量、现存量及付款申请占用。减少库存和应付，差额自动记为 RETURN_VARIANCE。
- GET /supplier-refunds：`finance:read`，退款记录含 order_code、supplier_name、project_name、amount、reference、note、operator_name、created_at。
- POST /supplier-refunds {payable_id,amount,reference,note}：`finance:write`，仅记录退货形成的实际到账退款，不能超过 paid_amount-amount，凭证唯一，支持部分退款；原 payments 和已付申请不改写。
- 所有新读写执行项目范围校验；新凭证最长 100 字符、原因最长 1000 字符，数量正数且最多 3 位。每个单据最多 100 条，不得重复 source_id。
- payables.amount 为入库减采购退货；paid_amount 为付款减退款；outstanding_amount 与 refundable_amount 分别取两者差的非负方向。付款/申请仍受库存验收、合同和其他申请占用约束。
- 项目及看板 order_amount/purchase_amount 为当前净采购订单额；趋势按原订单月份归集当前净额。receipt_amount 继续保留原累计入库，purchase_return_amount 另列；material_cost 扣项目退料，other_cost 包含有符号计价差额；paid_amount 为净实付，refund_amount/refund_due_amount 另列。

这些是实时管理报表，不是已结账的历史总账。迁移与完整验收见 [采购升级](PROCUREMENT-ERP-UPGRADE.md)。


## 试用增强 API（V6–V13）

金额字段和关联数组的授权以 [读取契约](READ-PERMISSIONS.md) 为准。无权限字段缺失，不用零伪装。原 estimated_profit 已移除，改为 registered_cost_balance，非完工利润预测。

- GET /dashboard -> 原数据 + workbench {role,groups[{key,title,total,items[{id,code,project_name,amount?,waiting_since?,waiting_days?,due_date?,overdue?,path}],path}],metric_keys,chart_keys,as_of}。等待时间不等于逾期。
- GET /records/{entity}?page=1&size=20&q=&status=&project_id=&supplier_id=&warehouse_id=&material_id=&order_id=&request_id=&contract_id=&id=&date_from=&date_to= -> {items,total,page,size}。size 1–100；每个 entity 只接受有语义的关联筛选，无制单日期的业务拒绝日期筛选。entity: projects,suppliers,materials,warehouses,requests,quotes,orders,contracts,cost-overview,expenses,receipts,issues,issue-returns,purchase-returns,inventory,stock-movements,stocktakes,payables,payments,payment-applications,supplier-refunds,supplier-statement,project-progress,approval-records,request-approvals,plan-history,plan-allocations。stocktakes 需要 stocktake:read 和 inventory:company，支持 status / warehouse_id；inventory 另支持 low_only=true；requests 支持 queue=quotes。列表先 SQL 分页再补本页明细；导出共用范围规则。
- GET /trace/requests/{id}?page=1&size=20；GET /trace/orders/{id}?page=1&size=20 -> {access,summary,order_items,links,events,total,page,size,source_notice?}。event: id,label,code,created_at,operator_name(nullable),status(nullable),reason,amount?(授权),path,lines[]。事件日期来自实际记录；创建事件不冒充当前状态。
- GET /issue-sources?project_id=&warehouse_id=&material_id= (`inventory:read` + `order:read`)；POST /issues items 可含可选 order_item_id，服务端验证明确来源，ISSUE_RETURN 保留同来源。
- GET /contracts/{id}；GET /contracts/{id}/payment-plan -> {version,stages,planned_total,unassigned_amount,allow_unassigned,legacy_warning,legacy_raw,actuals_supported,order_id,access,...}；无 finance:read 不返回实际关联金额/来源。历史及关联 UI 使用上述分页 API。
- PUT /contracts/{id}/payment-plan {expected_version,replace_legacy?,stages:[{id?,stage_name,due_date(nullable),amount,note}]}。允许未分配余额；旧内容不可解析时必须显式 replace_legacy=true，原文保存到历史。保留旧 header payment_plan 接口仅作兼容，沿用相同验证和权限，前端无 JSON 编辑器。
- POST /contracts/{id}/payment-allocations {stage_id,source_type:PAYMENT/REFUND,source_id,amount,reference}；POST /contracts/{id}/payment-allocations/{allocation}/void {reason}。需要合同读取和 finance:read/write，仅人工关联同一合同订单的真实记录，不改变 cash；reference 唯一、受源记录和阶段净额约束。
- GET /stocktakes?page=1&size=20&q=&status=&warehouse_id=；GET /stocktakes/{id}；GET /stocktakes/snapshot?warehouse_id=&material_id= -> quantity,revision,has_positive_price，计价字段按权限返回。
- POST /stocktakes {warehouse_id,note,correction_of?,items:[{material_id,snapshot_revision,counted_quantity,gain_unit_price?,reason}]}；PUT /stocktakes/{id} 另含 expected_version。
- POST /stocktakes/{id}/submit {expected_version}；/withdraw {expected_version,reason}；/refresh {expected_version,confirm_snapshot:true}；/review {expected_version,decision:APPROVED/REJECTED,comment}。每次修改 header version 递增。审批独立、库存快照失效返回409，普通保存/提交不改库存。
- GET /exchange/template/{suppliers|materials|projects|warehouses} -> xlsx；POST /exchange/preview/{entity} multipart file -> {hash,valid,strategy:CREATE_ONLY,count,valid_count,error_count,columns,rows[{row,data,input,errors}]}；POST /exchange/import/{entity} multipart file,expected_hash -> {batch_id,created}。服务端再次完整校验并原子写入。
- GET /exchange/export/{entity} -> xlsx，entity 为 suppliers,materials,projects,warehouses,requests,orders,contracts,cost-overview,expenses,inventory,stock-movements,receipts,issues,issue-returns,stocktakes,payables,payment-applications,payments,supplier-refunds,supplier-statement，支持对应 records 筛选，超过5000条拒绝。文本单元格不执行公式。
- GET /exchange/print/{orders|receipts|issues}/{id} -> text/html；GET /exchange/print/supplier-statement/0?筛选 -> 当前累计对账 HTML。正常 Bearer 鉴权，无公开打印 token；iframe 内独立 A4 文档，不含业务 UI。

所有新增写入使用服务端权限、参数/状态校验、事务及审计。无银行调用。盘点、来源、计划口径详见 [试用增强](TRIAL-UPGRADE.md)。
