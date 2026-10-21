-- Align only the original demo identities. Empty databases are seeded by DemoSeed.
-- Match tenant, original codes and names; preserve individually customized attributes.
-- Company is renamed last so every fixture update uses the same legacy-company guard.
-- Existing dates, amounts and document links are deliberately preserved.
-- Project 3 belongs to the existing enabled boss; no account/password is created.

UPDATE projects
SET name='滨江商务中心建设项目',
    owner=CASE WHEN owner='滨江置业有限公司' THEN '滨江商务置业有限公司' ELSE owner END,
    contractor=CASE WHEN contractor='筑企建设集团有限公司' THEN '华建工程建设有限公司' ELSE contractor END,
    location=CASE WHEN location='杭州市滨江区' THEN '杭州市滨江区商务中心地块' ELSE location END,
    type=CASE WHEN type='住宅' THEN '商业建筑' ELSE type END
WHERE tenant_id=1 AND code='PRJ-2026-001' AND name='滨江花园住宅一期'
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE projects
SET name='城南住宅小区二期工程',
    owner=CASE WHEN owner='城北城市发展集团' THEN '城南置业有限公司' ELSE owner END,
    contractor=CASE WHEN contractor='筑企建设集团有限公司' THEN '华建工程建设有限公司' ELSE contractor END,
    location=CASE WHEN location='杭州市拱墅区' THEN '杭州市萧山区城南住宅二期地块' ELSE location END,
    type=CASE WHEN type='市政' THEN '住宅' ELSE type END
WHERE tenant_id=1 AND code='PRJ-2026-002' AND name='城北市政道路改造'
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE projects
SET name='科技产业园机电安装工程',
    owner=CASE WHEN owner='高新投资发展有限公司' THEN '科技产业园投资发展有限公司' ELSE owner END,
    contractor=CASE WHEN contractor='筑企建设集团有限公司' THEN '华建工程建设有限公司' ELSE contractor END,
    location=CASE WHEN location='杭州市余杭区' THEN '杭州市余杭区科技产业园' ELSE location END,
    type=CASE WHEN type='工业' THEN '机电安装' ELSE type END,
    manager_id=CASE WHEN manager_id IN (SELECT u.id FROM users u JOIN roles r ON r.id=u.role_id WHERE u.tenant_id=1 AND u.username='manager' AND u.name='项目经理' AND r.code='manager') THEN COALESCE((SELECT u.id FROM users u JOIN roles r ON r.id=u.role_id WHERE u.tenant_id=1 AND u.username='boss' AND u.name='企业负责人' AND u.enabled=TRUE AND r.code='boss'),manager_id) ELSE manager_id END
WHERE tenant_id=1 AND code='PRJ-2026-003' AND name='高新产业园标准厂房'
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE dictionaries SET label='商业建筑', value='商业建筑'
WHERE tenant_id=1 AND category='project_type' AND label='住宅工程' AND value='住宅' AND sort_order=0
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE dictionaries SET label='住宅工程', value='住宅'
WHERE tenant_id=1 AND category='project_type' AND label='市政工程' AND value='市政' AND sort_order=1
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE dictionaries SET label='机电安装工程', value='机电安装'
WHERE tenant_id=1 AND category='project_type' AND label='工业厂房' AND value='工业' AND sort_order=2
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE materials SET name='螺纹钢筋'
WHERE tenant_id=1 AND code='MAT-001' AND name='螺纹钢'
  AND specification='HRB400E Φ16' AND unit='吨' AND category='钢材'
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE materials SET name='螺纹钢筋 · 2型'
WHERE tenant_id=1 AND code='MAT-011' AND name='螺纹钢 · 2型'
  AND specification='HRB400E Φ16' AND unit='吨' AND category='钢材'
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE materials SET name='螺纹钢筋 · 3型'
WHERE tenant_id=1 AND code='MAT-021' AND name='螺纹钢 · 3型'
  AND specification='HRB400E Φ16' AND unit='吨' AND category='钢材'
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE materials SET name='螺纹钢筋 · 4型'
WHERE tenant_id=1 AND code='MAT-031' AND name='螺纹钢 · 4型'
  AND specification='HRB400E Φ16' AND unit='吨' AND category='钢材'
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE warehouses SET location='滨江商务中心建设项目驻地'
WHERE tenant_id=1 AND code='WH-002' AND name='滨江项目现场仓' AND location='滨江花园项目驻地'
  AND EXISTS (SELECT 1 FROM company c WHERE c.id=1 AND c.name='筑企建设集团有限公司' AND c.system_name='筑企云 · 建筑企业经营平台');

UPDATE company
SET name='华建工程建设有限公司',
    theme_color=CASE WHEN theme_color='#0F766E' THEN '#2563EB' ELSE theme_color END
WHERE id=1 AND name='筑企建设集团有限公司' AND system_name='筑企云 · 建筑企业经营平台';
