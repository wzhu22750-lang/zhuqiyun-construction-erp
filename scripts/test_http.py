#!/usr/bin/env python3
"""Real HTTP/MySQL acceptance test. Adds traceable QA records; never resets data.
Usage: API_BASE=http://localhost:18080/api DEMO_PASSWORD=... python3 scripts/test_http.py
"""
import datetime as dt
import json
import os
import urllib.error
import urllib.request
import uuid
from decimal import Decimal as D

BASE = os.getenv('API_BASE', 'http://localhost:18080/api').rstrip('/')
PASSWORD = os.getenv('DEMO_PASSWORD', 'Zhuqi2026!')
RUN = 'QA-' + uuid.uuid4().hex[:10].upper()
TOKENS = {}
CHECKS = []


def call(role, method, path, body=None, expected=200, raw=None, content_type=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    headers = {'Content-Type': content_type or 'application/json'}
    if role:
        headers['Authorization'] = 'Bearer ' + TOKENS[role]
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=30)
        status, payload = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, payload = error.code, error.read()
    assert status == expected, f'{method} {path}: expected {expected}, got {status}: {payload[:600]!r}'
    if content_type == 'multipart/form-data; boundary=zqy-test':
        return json.loads(payload)['data']
    if path.endswith('/attachment') and method == 'GET':
        return payload
    result = json.loads(payload)
    if expected == 200:
        assert result['code'] == 0, result
        return result['data']
    return result


def check(name, condition=True):
    assert condition, name
    CHECKS.append(name)
    print('PASS', name)


def decimal(value):
    return D(str(value))


def metrics(project):
    return call('boss', 'GET', f'/projects/{project}')['metrics']


def main():
    for user in ('admin', 'boss', 'manager', 'buyer', 'warehouse', 'finance'):
        result = call(None, 'POST', '/auth/login', {'username': user, 'password': PASSWORD})
        TOKENS[user] = result['token']
        check(f'login {user}', result['user']['role'] == user)
    call(None, 'GET', '/projects', expected=401)
    check('anonymous API rejected')
    bootstrap = call('admin', 'GET', '/bootstrap')
    project = next(p for p in bootstrap['projects'] if p['name'] == '滨江商务中心建设项目')['id']
    material = next(m for m in bootstrap['materials'] if '钢筋' in m['name'])['id']
    suppliers = [s for s in bootstrap['suppliers'] if s['status'] == 'ACTIVE' and (not s.get('qualification_expiry') or s['qualification_expiry'] >= dt.date.today().isoformat())][:2]
    check('minimum seed counts', len(bootstrap['projects']) >= 3 and len(bootstrap['materials']) >= 40 and len(bootstrap['suppliers']) >= 10)
    before = metrics(project)
    dashboard_before = call('boss', 'GET', '/dashboard')['metrics']
    warehouse = call('admin', 'POST', '/warehouses', {'code': RUN, 'name': '验收隔离仓 ' + RUN, 'location': '自动化验收'})['id']
    request = call('manager', 'POST', '/requests', {
        'project_id': project, 'required_date': (dt.date.today() + dt.timedelta(days=15)).isoformat(),
        'note': RUN, 'items': [{'material_id': material, 'quantity': '10.000', 'unit_price': '3600.0000'}]})
    rid = request['id']
    call('buyer', 'POST', f'/requests/{rid}/orders', {'quote_id': 1, 'reason': RUN}, expected=409)
    check('unapproved request cannot order')
    call('manager', 'POST', f'/requests/{rid}/submit', {})
    call('manager', 'POST', f'/requests/{rid}/approve', {'decision': 'APPROVED', 'comment': RUN}, expected=403)
    check('manager cannot approve')
    call('boss', 'POST', f'/requests/{rid}/approve', {'decision': 'APPROVED', 'comment': '核验预算和需求，同意采购 ' + RUN})
    quotes = []
    for s, price, days in zip(suppliers, ('3500.0000', '3550.0000'), (3, 5)):
        quotes.append(call('buyer', 'POST', f'/requests/{rid}/quotes', {
            'supplier_id': s['id'], 'delivery_days': days, 'valid_until': (dt.date.today() + dt.timedelta(days=30)).isoformat(),
            'note': RUN, 'items': [{'request_item_id': request['items'][0]['id'], 'unit_price': price}]}))
    check('two supplier quotes persisted', len(call('buyer', 'GET', f'/requests/{rid}/quotes')) == 2)
    order = call('buyer', 'POST', f'/requests/{rid}/orders', {'quote_id': quotes[0]['id'], 'reason': '同规格总价较低且3日到货 ' + RUN})
    oid = order['id']
    check('order amount equals quoted lines', decimal(order['total_amount']) == D('35000.00'))
    call('buyer', 'POST', f'/requests/{rid}/orders', {'quote_id': quotes[1]['id'], 'reason': RUN}, expected=409)
    check('duplicate award rejected')
    receipt_body = {'warehouse_id': warehouse, 'reference': RUN + '-GR', 'note': '质检合格，部分到货', 'items': [{'order_item_id': order['items'][0]['id'], 'quantity': '6.000'}]}
    call('warehouse', 'POST', f'/orders/{oid}/receive', {**receipt_body, 'items': [{'order_item_id': order['items'][0]['id'], 'quantity': '11.000'}]}, expected=409)
    check('overreceipt rejected')
    receipt = call('warehouse', 'POST', f'/orders/{oid}/receive', receipt_body)
    check('partial receipt actual amount', decimal(receipt['amount']) == D('21000.00') and call('buyer', 'GET', f'/orders/{oid}')['status'] == 'PARTIAL')
    call('warehouse', 'POST', f'/orders/{oid}/receive', receipt_body, expected=409)
    check('duplicate receipt rejected')
    stock = next(i for i in call('admin', 'GET', '/inventory') if i['warehouse_id'] == warehouse and i['material_id'] == material)
    check('stock increased with moving average', decimal(stock['quantity']) == D('6') and decimal(stock['stock_value']) == D('21000') and decimal(stock['average_price']) == D('3500'))
    check('receipt does not create consumption cost', decimal(metrics(project)['material_cost']) == decimal(before['material_cost']))
    issue_body = {'project_id': project, 'warehouse_id': warehouse, 'reference': RUN + '-MI', 'note': RUN, 'items': [{'material_id': material, 'quantity': '2.000'}]}
    call('warehouse', 'POST', '/issues', {**issue_body, 'items': [{'material_id': material, 'quantity': '7.000'}]}, expected=409)
    check('insufficient stock rejected')
    issue = call('warehouse', 'POST', '/issues', issue_body)
    issue = next(i for i in call('admin','GET','/issues') if i['id']==issue['id'])
    check('project issue actual consumption', decimal(issue['amount']) == D('7000.00'))
    payable = next(p for p in call('finance', 'GET', '/payables') if p['order_id'] == oid)
    check('payable based on accepted receipt', decimal(payable['amount']) == D('21000.00'))
    call('warehouse', 'POST', '/payments', {'payable_id': payable['id'], 'amount': '1.00', 'reference': RUN}, expected=403)
    call('admin', 'POST', '/payments', {'payable_id': payable['id'], 'amount': '21000.01', 'reference': RUN}, expected=409)
    check('payment permission and amount enforced')
    payment_body = {'payable_id': payable['id'], 'amount': '10000.00', 'reference': RUN + '-PAY', 'note': RUN}
    call('finance', 'POST', '/payments', payment_body, expected=403)
    application = call('finance','POST','/payment-applications',{'payable_id':payable['id'],'amount':'10000.00','note':RUN})
    aid = application['id']
    call('finance','POST',f'/payment-applications/{aid}/pay',{'reference':RUN+'-EARLY'},expected=409)
    call('finance','POST',f'/payment-applications/{aid}/review',{'decision':'APPROVED'},expected=403)
    call('boss','POST',f'/payment-applications/{aid}/review',{'decision':'APPROVED','comment':RUN})
    call('finance','POST',f'/payment-applications/{aid}/pay',{'reference':payment_body['reference'],'note':RUN})
    call('finance','POST',f'/payment-applications/{aid}/pay',{'reference':RUN+'-AGAIN'},expected=409)
    call('admin', 'POST', '/payments', payment_body, expected=409)
    check('payment application requires independent approval and executes once')
    history = call('finance','GET',f'/payment-applications/{aid}/records')
    check('payment approval history persisted', [r['decision'] for r in history]==['SUBMITTED','APPROVED','PAID'])
    rejected = call('finance','POST','/payment-applications',{'payable_id':payable['id'],'amount':'11000.00','note':RUN})
    call('boss','POST',f"/payment-applications/{rejected['id']}/review",{'decision':'REJECTED','comment':'补齐凭证 '+RUN})
    withdrawn = call('finance','POST','/payment-applications',{'payable_id':payable['id'],'amount':'11000.00','note':RUN})
    call('finance','POST',f"/payment-applications/{withdrawn['id']}/cancel",{'reason':RUN})
    balance = next(p for p in call('finance','GET','/payables') if p['id']==payable['id'])
    check('rejection and withdrawal release reservations', decimal(balance['available_amount'])==D('11000') and decimal(balance['reserved_amount'])==0)
    check('partial payment and duplicate protection')
    after = metrics(project)
    for field, delta in [('request_amount','36000'),('order_amount','35000'),('receipt_amount','21000'),('material_cost','7000'),('actual_cost','7000'),('paid_amount','10000')]:
        check('project metric ' + field, decimal(after[field]) - decimal(before[field]) == D(delta))
    dashboard = call('boss', 'GET', '/dashboard')['metrics']
    check('dashboard database aggregation', decimal(dashboard['actual_cost']) - decimal(dashboard_before['actual_cost']) == D('7000') and decimal(dashboard['purchase_amount']) - decimal(dashboard_before['purchase_amount']) == D('35000') and decimal(dashboard['outstanding_amount']) - decimal(dashboard_before['outstanding_amount']) == D('11000'))
    own = call('manager', 'GET', '/projects')
    other = next((p for p in bootstrap['projects'] if p['id'] not in {x['id'] for x in own}), None)
    if other:
        call('manager', 'GET', f"/projects/{other['id']}", expected=403)
    check('manager project data scope', other is not None)
    # Reversible project CRUD and supplier/material CRUD leave no fake business history.
    manager = next(u for u in bootstrap['users'] if u['username'] == 'manager')['id']
    p = call('admin', 'POST', '/projects', {'code': RUN, 'name': RUN, 'owner': '验收建设方', 'contractor': '华建', 'manager_id': manager, 'location': '上海', 'type': '房屋建筑', 'contract_amount': '1000.00', 'budget_amount': '800.00', 'start_date': dt.date.today().isoformat(), 'end_date': (dt.date.today() + dt.timedelta(days=30)).isoformat(), 'progress': '0', 'status': 'PLANNING'})
    call('admin', 'PUT', f"/projects/{p['id']}", {'name': RUN + '修改'})
    call('admin', 'DELETE', f"/projects/{p['id']}")
    check('project create update delete')
    for endpoint, values in [('suppliers', {'code': RUN, 'name': RUN, 'contact':'验收', 'phone':'13800000000', 'category':'材料', 'status':'ACTIVE', 'qualification':'合格', 'qualification_expiry':'2030-12-31', 'rating':'4.50'}), ('materials', {'code':RUN, 'name':RUN, 'specification':'QA', 'unit':'吨', 'category':'钢材', 'safety_stock':'0.000', 'reference_price':'10.0000'})]:
        entity = call('admin', 'POST', '/' + endpoint, values)
        call('admin', 'PUT', f"/{endpoint}/{entity['id']}", {'name':RUN + '修改'})
        call('admin', 'DELETE', f"/{endpoint}/{entity['id']}")
        check(endpoint + ' create update delete')
    today = dt.date.today().isoformat()
    contract = call('admin', 'POST', '/contracts', {'code':RUN, 'name':'钢筋采购合同 ' + RUN, 'type':'PURCHASE', 'project_id':project, 'supplier_id':suppliers[0]['id'], 'order_id':oid, 'amount':'35000.00', 'signed_date':today, 'start_date':today, 'end_date':(dt.date.today()+dt.timedelta(days=30)).isoformat(), 'status':'ACTIVE', 'payment_plan':'[{"stage":"到货款","amount":"21000.00"}]'})
    attachment = b'Construction ERP contract attachment verification ' + RUN.encode()
    multipart = b'--zqy-test\r\nContent-Disposition: form-data; name="file"; filename="qa-contract.txt"\r\nContent-Type: text/plain\r\n\r\n' + attachment + b'\r\n--zqy-test--\r\n'
    call('admin','POST',f"/contracts/{contract['id']}/attachment",raw=multipart,content_type='multipart/form-data; boundary=zqy-test')
    check('contract authenticated attachment roundtrip', call('finance','GET',f"/contracts/{contract['id']}/attachment") == attachment)
    check('audit records persisted', any(RUN in str(a) for a in call('admin','GET','/audit-logs')))
    for role in TOKENS:
        call(role, 'POST', '/auth/logout', {})
    call('manager','GET','/auth/me',expected=401)
    check('logout invalidates token')
    print(json.dumps({'run':RUN,'checks':len(CHECKS),'project_id':project,'request_id':rid,'order_id':oid,'receipt_amount':'21000.00','consumed_cost':'7000.00','paid_amount':'10000.00','remaining_stock':'4.000','outstanding':'11000.00'},ensure_ascii=False))


if __name__ == '__main__':
    main()
