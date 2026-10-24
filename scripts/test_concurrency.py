#!/usr/bin/env python3
"""MySQL HTTP concurrent mutation checks; adds isolated QA data, never resets data."""
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import json
import threading
from decimal import Decimal
import test_http as api


def race(role, path, bodies):
    barrier = threading.Barrier(2)
    def invoke(body):
        barrier.wait(timeout=10)
        try:
            return ('accepted', api.call(role, 'POST', path, body))
        except AssertionError as error:
            assert 'got 409:' in str(error), str(error)
            return ('rejected', None)
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(invoke, bodies))
    assert sorted(r[0] for r in results) == ['accepted', 'rejected'], results
    return next(r[1] for r in results if r[0] == 'accepted')


def main():
    for role in ('admin', 'manager', 'boss', 'buyer', 'warehouse', 'finance'):
        api.TOKENS[role] = api.call(None, 'POST', '/auth/login', {'username':role,'password':api.PASSWORD})['token']
    bootstrap = api.call('admin','GET','/bootstrap')
    project = next(p['id'] for p in bootstrap['projects'] if p['name']=='滨江商务中心建设项目')
    material = bootstrap['materials'][0]['id']
    supplier = next(s['id'] for s in bootstrap['suppliers'] if s['status']=='ACTIVE' and (not s.get('qualification_expiry') or s['qualification_expiry']>=dt.date.today().isoformat()))
    warehouse = api.call('admin','POST','/warehouses',{'code':api.RUN,'name':'并发验收仓 '+api.RUN,'location':'QA'})['id']
    request = api.call('manager','POST','/requests',{'project_id':project,'required_date':(dt.date.today()+dt.timedelta(days=10)).isoformat(),'note':api.RUN,'items':[{'material_id':material,'quantity':'10.000','unit_price':'10.0000'}]})
    rid = request['id']
    api.call('manager','POST',f'/requests/{rid}/submit',{})
    api.call('boss','POST',f'/requests/{rid}/approve',{'decision':'APPROVED','comment':api.RUN})
    quote = api.call('buyer','POST',f'/requests/{rid}/quotes',{'supplier_id':supplier,'delivery_days':3,'valid_until':(dt.date.today()+dt.timedelta(days=30)).isoformat(),'items':[{'request_item_id':request['items'][0]['id'],'unit_price':'10.0000'}]})
    order = api.call('buyer','POST',f'/requests/{rid}/orders',{'quote_id':quote['id'],'reason':api.RUN})
    oid = order['id']
    race('warehouse',f'/orders/{oid}/receive',[{'warehouse_id':warehouse,'reference':api.RUN+'-GR-'+str(i),'items':[{'order_item_id':order['items'][0]['id'],'quantity':'6.000'}]} for i in range(2)])
    stock = next(s for s in api.call('admin','GET','/inventory') if s['warehouse_id']==warehouse and s['material_id']==material)
    api.check('concurrent receipts cannot exceed remaining order',Decimal(stock['quantity'])==6 and Decimal(stock['stock_value'])==60)
    race('warehouse','/issues',[{'project_id':project,'warehouse_id':warehouse,'reference':api.RUN+'-IS-'+str(i),'items':[{'material_id':material,'quantity':'4.000'}]} for i in range(2)])
    stock = next(s for s in api.call('admin','GET','/inventory') if s['warehouse_id']==warehouse and s['material_id']==material)
    api.check('concurrent issues cannot produce negative inventory',Decimal(stock['quantity'])==2 and Decimal(stock['stock_value'])==20)
    payable = next(p for p in api.call('finance','GET','/payables') if p['order_id']==oid)
    race('admin','/payments',[{'payable_id':payable['id'],'amount':'40.00','reference':api.RUN+'-PAY-'+str(i)} for i in range(2)])
    payable = next(p for p in api.call('finance','GET','/payables') if p['order_id']==oid)
    api.check('concurrent payments cannot exceed actual payable',Decimal(payable['paid_amount'])==40 and Decimal(payable['outstanding_amount'])==20)
    application = race('finance','/payment-applications',[{'payable_id':payable['id'],'amount':'15.00','note':api.RUN+str(i)} for i in range(2)])
    balance = next(p for p in api.call('finance','GET','/payables') if p['id']==payable['id'])
    api.check('concurrent applications cannot overreserve', Decimal(balance['reserved_amount'])==15 and Decimal(balance['available_amount'])==5)
    race('boss',f"/payment-applications/{application['id']}/review",[{'decision':'APPROVED','comment':api.RUN},{'decision':'APPROVED','comment':api.RUN}])
    reviewed = next(a for a in api.call('finance','GET','/payment-applications') if a['id']==application['id'])
    history = api.call('finance','GET',f"/payment-applications/{application['id']}/records")
    api.check('concurrent decisions transition once', len(history)==2 and reviewed['status'] in ('APPROVED','REJECTED'))
    race('finance',f"/payment-applications/{application['id']}/pay",[{'reference':api.RUN+'-APP-'+str(i)} for i in range(2)])
    api.check('concurrent execution posts once', len(api.call('finance','GET',f"/payment-applications/{application['id']}/records"))==3)
    final = next(p for p in api.call('finance','GET','/payables') if p['id']==payable['id'])
    for role in api.TOKENS:
        api.call(role,'POST','/auth/logout',{})
    print(json.dumps({'run':api.RUN,'checks':len(api.CHECKS),'order_id':oid,'warehouse_id':warehouse,'receipt':'60.00','issued':'40.00','stock':'20.00','paid':str(final['paid_amount']),'outstanding':str(final['outstanding_amount'])}))

if __name__=='__main__':
    main()
