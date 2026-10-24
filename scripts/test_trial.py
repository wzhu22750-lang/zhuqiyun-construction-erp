#!/usr/bin/env python3
"""Isolated Compose HTTP acceptance. Adds QA documents, never resets data. Stdlib only."""
import datetime as dt
import json,io,zipfile,urllib.request,urllib.error,uuid,threading
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal as D
import test_http as api

def call(role,path,body=None,expected=200,method='POST'):
 return api.call(role,method,path,body,expected=expected)
def binary(role,path,expected=200):
 req=urllib.request.Request(api.BASE+path,headers={'Authorization':'Bearer '+api.TOKENS[role]})
 try:
  with urllib.request.urlopen(req,timeout=30) as response:status,data=response.status,response.read()
 except urllib.error.HTTPError as e:status,data=e.code,e.read()
 assert status==expected,(path,status,data[:500]);return data

def upload(role,path,data,hash=None,expected=200):
 boundary='zqy-trial-'+uuid.uuid4().hex;raw=bytearray()
 if hash is not None:raw.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="expected_hash"\r\n\r\n{hash}\r\n'.encode())
 raw.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="trial.xlsx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n'.encode());raw.extend(data);raw.extend(f'\r\n--{boundary}--\r\n'.encode())
 return api.call(role,'POST',path,expected=expected,raw=raw,content_type='multipart/form-data; boundary='+boundary)
def workbook(template,rows):
 ns='http://schemas.openxmlformats.org/spreadsheetml/2006/main';ET.register_namespace('',ns)
 with zipfile.ZipFile(io.BytesIO(template)) as source:
  root=ET.fromstring(source.read('xl/worksheets/sheet1.xml'));sheet=root.find('{'+ns+'}sheetData')
  for index,values in enumerate(rows,2):
   row=ET.SubElement(sheet,'{'+ns+'}row',r=str(index))
   for col,value in enumerate(values):
    if value is None:continue
    address='';n=col+1
    while n:n,k=divmod(n-1,26);address=chr(65+k)+address
    cell=ET.SubElement(row,'{'+ns+'}c',r=address+str(index),t='inlineStr');inline=ET.SubElement(cell,'{'+ns+'}is');ET.SubElement(inline,'{'+ns+'}t').text=str(value)
  out=io.BytesIO()
  with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as target:
   for entry in source.infolist():target.writestr(entry,ET.tostring(root,encoding='utf-8') if entry.filename=='xl/worksheets/sheet1.xml' else source.read(entry.filename))
  return out.getvalue()
def parallel(functions):
 gate=threading.Barrier(len(functions))
 def go(fn):
  gate.wait(timeout=10)
  try:return 200,fn()
  except AssertionError as e:
   assert 'got 409:' in str(e),str(e);return 409,None
 with ThreadPoolExecutor(max_workers=len(functions)) as pool:return list(pool.map(go,functions))
def main():
 for role in ['admin','boss','manager','buyer','warehouse','finance']:api.TOKENS[role]=call(None,'/auth/login',{'username':role,'password':api.PASSWORD})['token']
 catalog=call('admin','/bootstrap',method='GET');project=next(p['id'] for p in catalog['projects'] if p['code']=='PRJ-2026-001');material=catalog['materials'][0]['id'];supplier=next(s['id'] for s in catalog['suppliers'] if s['status']=='ACTIVE' and (not s.get('qualification_expiry') or s['qualification_expiry']>=dt.date.today().isoformat()))
 serial=0
 def ref(s):
  nonlocal serial
  serial+=1;return api.RUN+'-'+s+'-'+str(serial)
 w=call('admin','/warehouses',{'code':ref('WH'),'name':'试用功能隔离仓','location':api.RUN})['id']
 def stock():return call('admin',f'/records/inventory?warehouse_id={w}&material_id={material}',method='GET')['items'][0]
 def count(qty,price=None,correction=None):
  snap=call('warehouse',f'/stocktakes/snapshot?warehouse_id={w}&material_id={material}',method='GET')
  return call('warehouse','/stocktakes',{'warehouse_id':w,'correction_of':correction,'note':api.RUN,'items':[{'material_id':material,'snapshot_revision':snap['revision'],'counted_quantity':qty,'gain_unit_price':price,'reason':'真实盘点验收'}]})
 def submit(h):return call('warehouse',f"/stocktakes/{h['id']}/submit",{'expected_version':h['version']})
 def review(h,role='boss',expected=200):return call(role,f"/stocktakes/{h['id']}/review",{'expected_version':h['version'],'decision':'APPROVED'},expected)
 h=count('10','10');api.check('count save does not create stock',call('admin',f'/records/inventory?warehouse_id={w}',method='GET')['total']==0)
 pending=submit(h);call('warehouse',f"/stocktakes/{h['id']}/review",{'expected_version':pending['version'],'decision':'APPROVED'},403)
 results=parallel([lambda:review(pending),lambda:review(pending)]);api.check('concurrent count review posts exactly once',sorted(s for s,_ in results)==[200,409] and D(stock()['quantity'])==10 and D(stock()['stock_value'])==100)
 call('manager',f"/stocktakes/{h['id']}",expected=403,method='GET');call('manager','/inventory',expected=403,method='GET');api.check('manager cannot read company stock or counts')
 old=submit(count('8'))
 issue=call('warehouse','/issues',{'project_id':project,'warehouse_id':w,'reference':ref('MI'),'items':[{'material_id':material,'quantity':'1'}]})
 issue_full=call('admin',f"/records/issues?id={issue['id']}",method='GET')['items'][0]
 call('warehouse',f"/issues/{issue['id']}/returns",{'reference':ref('IR'),'reason':'还原库存检查版本','items':[{'source_id':issue_full['items'][0]['id'],'quantity':'1'}]})
 review(old,expected=409);api.check('ABA inventory change still invalidates count snapshot',D(stock()['quantity'])==10)
 draft=call('warehouse',f"/stocktakes/{old['id']}/withdraw",{'expected_version':old['version'],'reason':'重新实盘'})
 fresh=call('warehouse',f"/stocktakes/{old['id']}/refresh",{'expected_version':draft['version'],'confirm_snapshot':True});approved=review(submit(fresh));api.check('refreshed snapshot loss uses moving average',D(stock()['quantity'])==8 and D(stock()['stock_value'])==80)
 corrected=review(submit(count('9',correction=approved['id'])));api.check('effective count correction is new linked document',corrected['correction_of']==approved['id'] and D(stock()['quantity'])==9)
 call('warehouse',f"/stocktakes/{corrected['id']}",{'warehouse_id':w,'expected_version':corrected['version'],'items':[]},409,method='PUT');api.check('effective count cannot edit')
 pending_before=next(g['total'] for g in call('boss','/dashboard',method='GET')['workbench']['groups'] if g['key']=='purchase_approval')
 request=call('manager','/requests',{'project_id':project,'required_date':(dt.date.today()+dt.timedelta(days=10)).isoformat(),'items':[{'material_id':material,'quantity':'10','unit_price':'10'}]});rid=request['id'];call('manager',f'/requests/{rid}/submit',{})
 boss=call('boss','/dashboard',method='GET');api.check('role workbench has real pending request',next(g['total'] for g in boss['workbench']['groups'] if g['key']=='purchase_approval')==pending_before+1)
 call('boss',f'/requests/{rid}/approve',{'decision':'APPROVED','comment':api.RUN});quote=call('buyer',f'/requests/{rid}/quotes',{'supplier_id':supplier,'delivery_days':2,'valid_until':(dt.date.today()+dt.timedelta(days=30)).isoformat(),'items':[{'request_item_id':request['items'][0]['id'],'unit_price':'10'}]});o=call('buyer',f'/requests/{rid}/orders',{'quote_id':quote['id'],'reason':api.RUN});oid=o['id']
 for qty in ['3','3']:call('warehouse',f'/orders/{oid}/receive',{'warehouse_id':w,'reference':ref('GR'),'items':[{'order_item_id':o['items'][0]['id'],'quantity':qty}]})
 receipt=call('warehouse',f'/records/receipts?order_id={oid}',method='GET')['items'][0];payable=call('finance',f'/records/payables?order_id={oid}',method='GET')['items'][0]
 app=call('finance','/payment-applications',{'payable_id':payable['id'],'amount':'50'});call('boss',f"/payment-applications/{app['id']}/review",{'decision':'APPROVED','comment':api.RUN});paid=call('finance',f"/payment-applications/{app['id']}/pay",{'reference':ref('PAY')})
 call('warehouse',f"/receipts/{receipt['id']}/returns",{'reference':ref('RET'),'reason':'验收缺陷','items':[{'source_id':receipt['items'][0]['id'],'quantity':'2'}]})
 refund=call('finance','/supplier-refunds',{'payable_id':payable['id'],'amount':'10','reference':ref('REF')})
 traced=call('admin',f'/trace/orders/{oid}?size=100',method='GET');s=traced['summary']['payable'];api.check('partial receipts return payment refund reconcile',D(s['amount'])==40 and D(s['paid_amount'])==40 and D(s['refundable_amount'])==0 and D(traced['order_items'][0]['remaining_quantity'])==4)
 api.check('trace uses persisted approval cash and return events',all(any(e['id'].startswith(prefix) for e in traced['events']) for prefix in ['request-','approval-','quote-','order-','receipt-','return-','payment-review-','payment-','refund-']))
 hidden=call('buyer',f'/orders/{oid}',method='GET');api.check('buyer has no cash detail even direct API','payable' not in hidden and not hidden['payments'] and not hidden['refunds']);call('buyer',f'/trace/orders/{oid}',method='GET');call('buyer',f'/records/payables?order_id={oid}',expected=403,method='GET')
 for role in ['manager','buyer','warehouse','finance','boss']:
  work=call(role,'/dashboard',method='GET')['workbench'];api.check(role+' workbench has three to five relevant groups',3<=len(work['groups'])<=5)
 manager=next(u for u in catalog['users'] if u['username']=='boss');other=call('admin','/projects',{'code':ref('OTHER'),'name':'隔离项目','owner':'QA','contractor':'QA','manager_id':manager['id'],'contract_amount':'100','budget_amount':'80','start_date':dt.date.today().isoformat(),'end_date':dt.date.today().isoformat()});call('manager',f"/projects/{other['id']}",expected=403,method='GET');api.check('manager scoped paginated query',call('manager',f"/records/projects?id={other['id']}",method='GET')['total']==0)
 contract=call('finance','/contracts',{'code':ref('CT'),'name':'试用付款计划','type':'PURCHASE','project_id':project,'supplier_id':supplier,'order_id':oid,'amount':'100','signed_date':dt.date.today().isoformat(),'start_date':dt.date.today().isoformat(),'end_date':(dt.date.today()+dt.timedelta(days=30)).isoformat(),'status':'ACTIVE'})
 cid=contract['id'];plan=call('finance',f'/contracts/{cid}/payment-plan',method='GET');call('finance',f'/contracts/{cid}/payment-plan',{'expected_version':plan['version'],'stages':[{'stage_name':'到货款','due_date':dt.date.today().isoformat(),'amount':'60','note':api.RUN}]},method='PUT');plan=call('finance',f'/contracts/{cid}/payment-plan',method='GET');api.check('editable payment plan allows explicit unassigned balance',D(plan['unassigned_amount'])==40)
 call('finance',f'/contracts/{cid}/payment-plan',{'expected_version':plan['version'],'stages':[{'stage_name':'错金额','amount':'100.001'}]},400,method='PUT');call('finance',f'/contracts/{cid}/payment-plan',{'expected_version':0,'stages':[]},409,method='PUT')
 stage=plan['stages'][0]['id'];payment=plan['payments'][0]['id'];call('finance',f'/contracts/{cid}/payment-allocations',{'stage_id':stage,'source_type':'PAYMENT','source_id':payment,'amount':'50','reference':ref('LINK')});call('finance',f'/contracts/{cid}/payment-allocations',{'stage_id':stage,'source_type':'REFUND','source_id':refund['id'],'amount':'10','reference':ref('UNLINK')});plan=call('finance',f'/contracts/{cid}/payment-plan',method='GET');api.check('manual plan association keeps real payments independent',D(plan['stages'][0]['linked_net_paid'])==40)
 template=binary('admin','/exchange/template/materials');code=ref('MAT');invalid=workbook(template,[[code,'合法','规格','吨','钢材','0','1'],[ref('BAD'),'错误','规格','吨','钢材','0','1.00001']]);preview=upload('admin','/exchange/preview/materials',invalid);api.check('import preview gives row errors',not preview['valid'] and any(r['errors'] for r in preview['rows']));upload('admin','/exchange/import/materials',invalid,preview['hash'],400);api.check('invalid import writes no rows',call('admin','/records/materials?q='+code,method='GET')['total']==0)
 valid=workbook(template,[[code,'=SUM(1,2)','规格','吨','钢材','0','1.25']]);preview=upload('admin','/exchange/preview/materials',valid);upload('admin','/exchange/import/materials',valid,preview['hash']);upload('admin','/exchange/import/materials',valid,preview['hash'],400);api.check('valid import creates once without overriding')
 exported=binary('admin','/exchange/export/materials?q='+code)
 with zipfile.ZipFile(io.BytesIO(exported)) as book:api.check('Excel text is never a formula',b'<f>' not in book.read('xl/worksheets/sheet1.xml'))
 binary('warehouse','/exchange/export/supplier-statement',403);html=binary('warehouse',f"/exchange/print/issues/{issue['id']}").decode();api.check('quantity-only A4 print hides cost',('size:A4' in html and '签字' in html and '原始金额合计' not in html));statement=binary('finance',f'/exchange/export/supplier-statement?order_id={oid}');api.check('filtered supplier statement exports real balances',statement.startswith(b'PK'))
 html=binary('buyer',f'/exchange/print/orders/{oid}').decode();api.check('order print contains company document project and signatures',all(x in html for x in [o['code'],'签字','项目','待履行']))
 for i in range(51):call('warehouse',f'/orders/{oid}/receive',{'warehouse_id':w,'reference':ref('PAGE'),'items':[{'order_item_id':o['items'][0]['id'],'quantity':'0.001'}]})
 first=call('admin',f'/trace/orders/{oid}?size=20&page=1',method='GET');last=call('admin',f'/trace/orders/{oid}?size=20&page=4',method='GET');api.check('trace and receipt lists retain history beyond fifty rows',first['total']>60 and len(first['events'])==20 and len(last['events'])>0 and call('warehouse',f'/records/receipts?order_id={oid}&size=20',method='GET')['total']==53)
 print(json.dumps({'run':api.RUN,'checks':len(api.CHECKS),'order_id':oid,'request_id':rid,'contract_id':cid,'stocktake_id':corrected['id'],'warehouse_id':w},ensure_ascii=False))
if __name__=='__main__':main()
