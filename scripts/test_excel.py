#!/usr/bin/env python3
"""Excel HTTP acceptance against an isolated demo database. Adds uniquely named QA records."""
import io
import json
import os
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
import test_http as api
from test_trial import binary, upload, workbook

NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}


def sheet(data, name='sheet1'):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return ET.fromstring(archive.read(f'xl/worksheets/{name}.xml'))


def texts(data, name='sheet1'):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        strings = []
        if 'xl/sharedStrings.xml' in archive.namelist():
            strings = [''.join(node.itertext()) for node in ET.fromstring(archive.read('xl/sharedStrings.xml')).findall('s:si', NS)]
        root = ET.fromstring(archive.read(f'xl/worksheets/{name}.xml'))
        result = []
        for row in root.findall('s:sheetData/s:row', NS):
            values = []
            for cell in row.findall('s:c', NS):
                value = cell.find('s:v', NS)
                text = value.text if value is not None else ''
                if cell.get('t') == 's':
                    text = strings[int(text)]
                elif cell.get('t') == 'inlineStr':
                    text = ''.join(cell.find('s:is', NS).itertext())
                values.append(text)
            result.append(values)
        return result


def main():
    for role in ['admin', 'manager', 'warehouse', 'finance']:
        api.TOKENS[role] = api.call(None, 'POST', '/auth/login', {'username': role, 'password': api.PASSWORD})['token']
    tag = api.RUN
    templates = {entity: binary('admin', '/exchange/template/' + entity) for entity in ['suppliers', 'materials', 'projects', 'warehouses']}
    api.check('all four import templates are valid xlsx', all(len(texts(data)) == 1 for data in templates.values()))
    codes = {entity: '000-' + tag + '-' + entity for entity in templates}
    bad = workbook(templates['warehouses'], [[codes['warehouses'], 'Excel验收仓', tag], [tag + '-BAD', '', '缺少名称']])
    preview = upload('admin', '/exchange/preview/warehouses', bad)
    api.check('warehouse preview reports row numbers and success/error counts', preview['count'] == 2 and preview['valid_count'] == 1 and preview['error_count'] == 1 and preview['rows'][1]['row'] == 3 and preview['columns']['name'])
    upload('admin', '/exchange/import/warehouses', bad, preview['hash'], 400)
    api.check('invalid warehouse batch writes no rows', api.call('admin', 'GET', '/records/warehouses?q=' + tag)['total'] == 0)
    inputs = {
        'warehouses': [[codes['warehouses'], 'Excel验收仓', tag]],
        'suppliers': [[codes['suppliers'], '=SUM(1,2)', 'Excel联系人', '+8613800000000', '钢材', 'ACTIVE', '', '', '4.5']],
        'materials': [[codes['materials'], 'Excel验收材料', 'QA规格', '件', '测试材料', '1.125', '12.3456']],
        'projects': [[codes['projects'], 'Excel验收项目', '测试建设单位', '测试承建单位', 'manager', tag, 'TEST', '1000.25', '800.10', '2026-10-22', '2026-12-31', '0', 'PLANNING']],
    }
    imported = {}
    for entity, rows in inputs.items():
        data = workbook(templates[entity], rows)
        checked = upload('admin', '/exchange/preview/' + entity, data)
        assert checked['valid'], checked
        assert checked['rows'][0]['input']['code'] == codes[entity]
        result = upload('admin', '/exchange/import/' + entity, data, checked['hash'])
        assert result['created'] == 1
        saved = api.call('admin', 'GET', '/records/' + entity + '?q=' + codes[entity])['items']
        assert len(saved) == 1 and saved[0]['code'] == codes[entity]
        imported[entity] = saved[0]['id']
        upload('admin', '/exchange/import/' + entity, data, checked['hash'], 400)
        api.check(entity + ' import persists once and preserves original code')
    api.check('warehouse/material import does not create opening inventory', api.call('admin', 'GET', '/records/inventory?warehouse_id=' + str(imported['warehouses']))['total'] == 0)
    exports = ['suppliers', 'materials', 'projects', 'warehouses', 'requests', 'orders', 'contracts', 'cost-overview', 'expenses', 'inventory', 'stock-movements', 'receipts', 'issues', 'issue-returns', 'stocktakes', 'payables', 'payment-applications', 'payments', 'supplier-refunds', 'supplier-statement']
    for entity in exports:
        data = binary('admin', '/exchange/export/' + entity)
        assert texts(data)[0], entity
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            assert all(not ET.fromstring(archive.read(name)).findall('.//s:f', NS) for name in archive.namelist() if name.startswith('xl/worksheets/sheet') and name.endswith('.xml')), entity
    api.check('twenty exports are readable workbooks without executable formulas')
    exported = binary('admin', '/exchange/export/suppliers?q=' + codes['suppliers'])
    rows = texts(exported)
    api.check('filtered supplier export preserves formula-like text and phone number', len(rows) == 2 and rows[1][1] == '=SUM(1,2)' and rows[1][3] == '+8613800000000')
    stock_headers = texts(binary('warehouse', '/exchange/export/inventory'))[0]
    movement_headers = texts(binary('warehouse', '/exchange/export/stock-movements'))[0]
    api.check('quantity-only role exports hide valuations', not any(value in stock_headers + movement_headers for value in ['加权均价', '库存金额', '单位成本', '金额']))
    binary('warehouse', '/exchange/export/payments', 403)
    binary('warehouse', '/exchange/template/warehouses', 403)
    binary('manager', '/exchange/export/stocktakes', 403)
    api.check('export and import permissions are enforced by server')
    other = next(p for p in api.call('admin', 'GET', '/records/projects')['items'] if p['code'] == 'PRJ-2026-003')
    isolated = binary('manager', '/exchange/export/requests?project_id=' + str(other['id']))
    api.check('manager export excludes projects outside assigned scope', len(texts(isolated)) == 1)
    out = os.getenv('EXCEL_QA_DIR')
    if out:
        target = Path(out)
        target.mkdir(parents=True, exist_ok=True)
        # A fresh file for browser import: these two rows have not been committed by the API suite.
        (target / 'warehouse-valid.xlsx').write_bytes(workbook(templates['warehouses'], [[tag + '-BROWSER', '浏览器Excel仓库', tag]]))
        (target / 'warehouse-invalid.xlsx').write_bytes(workbook(templates['warehouses'], [[tag + '-UI-BAD', '', tag]]))
        (target / 'supplier-export.xlsx').write_bytes(exported)
    print(json.dumps({'run': tag, 'checks': len(api.CHECKS), 'exports': len(exports), 'imported': imported}, ensure_ascii=False))


if __name__ == '__main__':
    main()
