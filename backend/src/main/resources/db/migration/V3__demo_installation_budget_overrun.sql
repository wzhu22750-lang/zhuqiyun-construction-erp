-- A bounded repair for the exact original third demo fixture after V2.
-- All four original cost rows and the matching MAIN contract must still exist unchanged.
-- A renamed company/project, reassigned manager, changed amount/description/operator,
-- extra expense or modified MAIN contract opts the entire project out of this repair.
-- No dates, payments, payables, stock or custom records are rewritten.
CREATE TEMPORARY TABLE zqy_v3_original_fixture (project_id BIGINT PRIMARY KEY);
START TRANSACTION;

INSERT INTO zqy_v3_original_fixture (project_id)
SELECT p.id FROM projects p
JOIN company c ON c.id=p.tenant_id
JOIN users boss ON boss.id=p.manager_id AND boss.tenant_id=1
JOIN roles br ON br.id=boss.role_id
WHERE p.tenant_id=1
  AND c.name='华建工程建设有限公司' AND c.system_name='筑企云 · 建筑企业经营平台'
  AND p.code='PRJ-2026-003' AND p.name='科技产业园机电安装工程'
  AND p.owner='科技产业园投资发展有限公司' AND p.contractor='华建工程建设有限公司'
  AND p.location='杭州市余杭区科技产业园' AND p.type='机电安装'
  AND p.contract_amount=36000000.00 AND p.budget_amount=29000000.00
  AND p.progress=25.00 AND p.status='ACTIVE'
  AND boss.username='boss' AND boss.name='企业负责人' AND boss.enabled=TRUE AND br.code='boss'
  AND (SELECT COUNT(*) FROM contracts ct WHERE ct.project_id=p.id AND ct.type='MAIN')=1
  AND EXISTS (SELECT 1 FROM contracts ct WHERE ct.tenant_id=1 AND ct.project_id=p.id
      AND ct.code='CT-2026-003' AND ct.name='建设工程施工总承包合同' AND ct.type='MAIN'
      AND ct.amount=36000000.00 AND ct.status='ACTIVE' AND ct.supplier_id IS NULL AND ct.order_id IS NULL)
  AND (SELECT COUNT(*) FROM costs x WHERE x.project_id=p.id)=4
  AND (SELECT COUNT(*) FROM costs x
       JOIN users f ON f.id=x.operator_id AND f.tenant_id=1
       JOIN roles fr ON fr.id=f.role_id
       WHERE x.project_id=p.id AND x.tenant_id=1
         AND f.username='finance' AND f.name='财务专员' AND fr.code='finance'
         AND ((x.category='LABOR' AND x.amount=495000.00 AND x.description='现场工人工资')
           OR (x.category='MACHINE' AND x.amount=144000.00 AND x.description='塔吊及挖机租赁')
           OR (x.category='SUBCONTRACT' AND x.amount=660000.00 AND x.description='土建专业分包进度款')
           OR (x.category='OTHER' AND x.amount=37500.00 AND x.description='现场临设及水电费')))=4
  AND (SELECT COUNT(DISTINCT x.category) FROM costs x WHERE x.project_id=p.id)=4;

UPDATE costs
SET amount=CASE category WHEN 'LABOR' THEN 600000.00 WHEN 'MACHINE' THEN 180000.00
             WHEN 'SUBCONTRACT' THEN 720000.00 WHEN 'OTHER' THEN 75000.00 END,
    description=CASE category WHEN 'LABOR' THEN '机电安装班组人工费' WHEN 'MACHINE' THEN '吊装及调试设备租赁'
             WHEN 'SUBCONTRACT' THEN '机电安装专业分包进度款' WHEN 'OTHER' THEN '现场临设及水电费' END
WHERE tenant_id=1 AND project_id IN (SELECT project_id FROM zqy_v3_original_fixture);

UPDATE contracts SET amount=1800000.00
WHERE tenant_id=1 AND code='CT-2026-003' AND type='MAIN'
  AND project_id IN (SELECT project_id FROM zqy_v3_original_fixture);

UPDATE projects SET contract_amount=1800000.00, budget_amount=1450000.00
WHERE tenant_id=1 AND id IN (SELECT project_id FROM zqy_v3_original_fixture);

COMMIT;
DROP TEMPORARY TABLE zqy_v3_original_fixture;
