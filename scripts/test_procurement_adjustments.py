#!/usr/bin/env python3
"""Real HTTP/MySQL checks for append-only procurement corrections. Adds isolated QA records."""
import datetime as dt
import json
from decimal import Decimal as D
from concurrent.futures import ThreadPoolExecutor
import threading
import test_http as api


def call(role, path, body=None, expected=200, method='POST'):
    return api.call(role, method, path, body, expected=expected)


def race(operations):
    gate = threading.Barrier(len(operations))
    def invoke(op):
        gate.wait(timeout=10)
        try:
            return ('accepted', call(*op))
        except AssertionError as error:
            assert 'got 409:' in str(error), str(error)
            return ('rejected', None)
    with ThreadPoolExecutor(max_workers=len(operations)) as pool:
        results = list(pool.map(invoke, operations))
    assert sorted(r[0] for r in results) == ['accepted', 'rejected'], results
    return next(r[1] for r in results if r[0] == 'accepted')


def main():
    for role in ('admin','boss','manager','buyer','warehouse','finance'):
        api.TOKENS[role] = call(None,'/auth/login',{'username':role,'password':api.PASSWORD})['token']
    catalog=call('admin','/bootstrap',method='GET')
    project=next(p['id'] for p in catalog['projects'] if p['name']=='滨江商务中心建设项目')
    material=catalog['materials'][0]['id']
    suppliers=[s['id'] for s in catalog['suppliers'] if s['status']=='ACTIVE' and (not s.get('qualification_expiry') or s['qualification_expiry']>=dt.date.today().isoformat())][:2]
    serial=0
    def ref(suffix):
        nonlocal serial
        serial+=1
        return api.RUN+'-'+suffix+'-'+str(serial)
    def warehouse():
        code=ref('WH')
        return call('admin','/warehouses',{'code':code,'name':'采购纠错验收仓 '+code,'location':api.RUN})['id']
    def order(price='10.00'):
        request=call('manager','/requests',{'project_id':project,'required_date':(dt.date.today()+dt.timedelta(days=10)).isoformat(),'note':api.RUN,'items':[{'material_id':material,'quantity':'10.000','unit_price':price}]})
        rid=request['id'];call('manager',f'/requests/{rid}/submit',{});call('boss',f'/requests/{rid}/approve',{'decision':'APPROVED','comment':api.RUN})
        quotes=[]
        for supplier,p in zip(suppliers,[D(price),D(price)+D('.5')]):
            quotes.append(call('buyer',f'/requests/{rid}/quotes',{'supplier_id':supplier,'delivery_days':3,'valid_until':(dt.date.today()+dt.timedelta(days=30)).isoformat(),'items':[{'request_item_id':request['items'][0]['id'],'unit_price':str(p)}]}))
        return call('buyer',f'/requests/{rid}/orders',{'quote_id':quotes[0]['id'],'reason':api.RUN})
    def receive(o,w,qty):
        return call('warehouse',f"/orders/{o['id']}/receive",{'warehouse_id':w,'reference':ref('GR'),'items':[{'order_item_id':o['items'][0]['id'],'quantity':qty}]})
    def stock(w):
        return next(i for i in call('warehouse','/inventory',method='GET') if i['warehouse_id']==w and i['material_id']==material)
    def payable(o):
        return next(p for p in call('finance','/payables',method='GET') if p['order_id']==o['id'])
    def receipt_line(o,r):
        detail=call('buyer',f"/orders/{o['id']}",method='GET')
        return next(g for g in detail['receipts'] if g['id']==r['id'])['items'][0]['id']
    def return_body(source,qty):
        return {'reference':ref('RET'),'reason':api.RUN,'items':[{'source_id':source,'quantity':qty}]}
    def pay(o,amount):
        a=call('finance','/payment-applications',{'payable_id':payable(o)['id'],'amount':amount,'note':api.RUN})
        call('boss',f"/payment-applications/{a['id']}/review",{'decision':'APPROVED','comment':api.RUN})
        return call('finance',f"/payment-applications/{a['id']}/pay",{'reference':ref('PAY')})

    w=warehouse();o=order();r=receive(o,w,'6.000')
    issue=call('warehouse','/issues',{'project_id':project,'warehouse_id':w,'reference':ref('MI'),'items':[{'material_id':material,'quantity':'2.000'}]})
    source=next(i for i in call('warehouse','/issues',method='GET') if i['id']==issue['id'])['items'][0]['id']
    back=call('warehouse',f"/issues/{issue['id']}/returns",return_body(source,'1.000'))
    api.check('project return restores original cost without cash',D(back['amount'])==10 and D(stock(w)['stock_value'])==50 and D(payable(o)['amount'])==60 and D(payable(o)['paid_amount'])==0)
    call('warehouse',f"/issues/{issue['id']}/returns",return_body(source,'2.000'),expected=409)
    api.check('project return cannot exceed unreturned issue quantity')
    closed=call('buyer',f"/orders/{o['id']}/close",{'reason':api.RUN})
    api.check('close preserves original amount and accepted payable',closed['status']=='CLOSED' and D(closed['total_amount'])==100 and D(closed['closed_amount'])==40 and D(closed['effective_amount'])==60 and D(payable(o)['amount'])==60)
    call('warehouse',f"/orders/{o['id']}/receive",{'warehouse_id':w,'reference':ref('LATE'),'items':[{'order_item_id':o['items'][0]['id'],'quantity':'1.000'}]},expected=409)
    api.check('closed order rejects later receipt')
    draft=call('manager',f"/orders/{o['id']}/reorder-request",{'required_date':(dt.date.today()+dt.timedelta(days=20)).isoformat(),'note':api.RUN})
    api.check('replacement is a linked draft for remaining quantities',draft['status']=='DRAFT' and draft['source_order_id']==o['id'] and D(draft['items'][0]['quantity'])==4)
    call('manager',f"/orders/{o['id']}/reorder-request",{'required_date':dt.date.today().isoformat()},expected=409)
    api.check('replacement cannot be generated twice')
    original_payment=pay(o,'60.00');source_receipt=receipt_line(o,r)
    ret=call('warehouse',f"/receipts/{r['id']}/returns",return_body(source_receipt,'3.000'))
    p=payable(o)
    api.check('paid purchase return creates refundable balance',D(ret['amount'])==30 and D(p['amount'])==30 and D(p['paid_amount'])==60 and D(p['outstanding_amount'])==0 and D(p['refundable_amount'])==30)
    refund={'payable_id':p['id'],'amount':'10.00','reference':ref('REFUND'),'note':api.RUN}
    call('finance','/supplier-refunds',refund);call('finance','/supplier-refunds',refund,expected=409)
    call('finance','/supplier-refunds',{'payable_id':p['id'],'amount':'20.01','reference':ref('OVER')},expected=409)
    api.check('partial refunds are unique and bounded',D(payable(o)['refundable_amount'])==20)
    call('finance','/supplier-refunds',{'payable_id':p['id'],'amount':'20.00','reference':ref('LAST')})
    p=payable(o)
    api.check('net cash reconciles while original payment stays immutable',D(p['paid_amount'])==30 and D(p['refunded_amount'])==30 and D(next(t for t in call('finance','/payments',method='GET') if t['id']==original_payment['id'])['amount'])==60)
    detail=call('buyer',f"/orders/{o['id']}",method='GET')
    api.check('order trace includes return refund and replacement',len(detail['returns'])==1 and len(detail['refunds'])==2 and detail['replacement_request_id']==draft['id'] and any(e['action']=='RETURN_PURCHASE' for e in detail['events']))
    call('buyer',f"/receipts/{r['id']}/returns",return_body(source_receipt,'1'),expected=403)
    call('warehouse','/supplier-refunds',{'payable_id':p['id'],'amount':'1','reference':ref('DENIED')},expected=403)
    api.check('warehouse procurement and finance permissions stay separated')

    w2=warehouse();o2=order();r2=receive(o2,w2,'6');a=call('finance','/payment-applications',{'payable_id':payable(o2)['id'],'amount':'40'})
    body=return_body(receipt_line(o2,r2),'3');call('warehouse',f"/receipts/{r2['id']}/returns",body,expected=409)
    api.check('reservation conflict rolls back every stock change',D(stock(w2)['quantity'])==6 and D(stock(w2)['stock_value'])==60 and D(payable(o2)['amount'])==60)
    call('finance',f"/payment-applications/{a['id']}/cancel",{'reason':api.RUN});call('warehouse',f"/receipts/{r2['id']}/returns",body)
    api.check('withdrawn payment releases return constraint',D(payable(o2)['amount'])==30)

    w3=warehouse();oa=order('10');ob=order('20');ra=receive(oa,w3,'10');rb=receive(ob,w3,'10')
    low=call('warehouse',f"/receipts/{ra['id']}/returns",return_body(receipt_line(oa,ra),'2'))
    high=call('warehouse',f"/receipts/{rb['id']}/returns",return_body(receipt_line(ob,rb),'2'))
    api.check('moving-average variance is signed and does not corrupt stock',D(low['variance_amount'])==10 and D(high['variance_amount'])==-10 and D(stock(w3)['quantity'])==16 and D(stock(w3)['stock_value'])==240)

    extra=call('warehouse',f"/receipts/{rb['id']}/returns",return_body(receipt_line(ob,rb),'1'))
    dashboard=call('boss','/dashboard',method='GET')
    api.check('negative pricing variance remains visible in ERP reporting',D(extra['variance_amount'])==-5 and any(D(c['value'])<0 for c in dashboard['cost_composition']))

    # Each race is a real concurrent pair against one resource, with no reset or deletes.
    wr=warehouse();orr=order();rr=receive(orr,wr,'6');issued=call('warehouse','/issues',{'project_id':project,'warehouse_id':wr,'reference':ref('RACE-MI'),'items':[{'material_id':material,'quantity':'3'}]})
    src=next(i for i in call('warehouse','/issues',method='GET') if i['id']==issued['id'])['items'][0]['id']
    race([('warehouse',f"/issues/{issued['id']}/returns",return_body(src,'2')) for _ in range(2)])
    api.check('concurrent project returns cannot exceed original issue',D(stock(wr)['quantity'])==5)
    ws=warehouse();os=order();rs=receive(os,ws,'6');src=receipt_line(os,rs)
    race([('warehouse',f"/receipts/{rs['id']}/returns",return_body(src,'4')) for _ in range(2)])
    api.check('concurrent purchase returns cannot overcredit payable',D(stock(ws)['quantity'])==2 and D(payable(os)['amount'])==20)
    call('buyer',f"/orders/{os['id']}/close",{'reason':api.RUN})
    race([('manager',f"/orders/{os['id']}/reorder-request",{'required_date':dt.date.today().isoformat()}) for _ in range(2)])
    api.check('concurrent replacements create one draft')
    wf=warehouse();of=order();rf=receive(of,wf,'6');pay(of,'60');call('warehouse',f"/receipts/{rf['id']}/returns",return_body(receipt_line(of,rf),'3'));pf=payable(of)
    race([('finance','/supplier-refunds',{'payable_id':pf['id'],'amount':'20','reference':ref('RACE-REF')}) for _ in range(2)])
    api.check('concurrent refunds cannot exceed receivable',D(payable(of)['refundable_amount'])==10)
    wc=warehouse();oc=order();receive(oc,wc,'6')
    race([('buyer',f"/orders/{oc['id']}/close",{'reason':api.RUN}),('warehouse',f"/orders/{oc['id']}/receive",{'warehouse_id':wc,'reference':ref('RACE-GR'),'items':[{'order_item_id':oc['items'][0]['id'],'quantity':'4'}]})])
    terminal=call('buyer',f"/orders/{oc['id']}",method='GET')
    api.check('close versus receipt serializes to one valid terminal state',terminal['status'] in ('CLOSED','COMPLETED') and all(D(i['received_quantity'])+D(i['closed_quantity'])==D(i['quantity']) for i in terminal['items']))
    for role in api.TOKENS:call(role,'/auth/logout',{})
    print(json.dumps({'run':api.RUN,'checks':len(api.CHECKS),'main_order_id':o['id'],'replacement_request_id':draft['id'],'paid_after_refunds':'30.00','remaining_supplier_refund':'0.00','concurrency_pairs':5}))


if __name__=='__main__':main()
