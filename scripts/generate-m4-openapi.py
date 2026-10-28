#!/usr/bin/env python3
"""Contrato expansivo M4; preserva byte-semánticamente operaciones/esquemas M0–M3."""
import json
from pathlib import Path
root = Path(__file__).resolve().parent.parent
spec = json.loads((root / 'docs/openapi/m3.json').read_text())
spec['info']['version'] = '0.4.1'
def ref(name): return {'$ref': '#/components/schemas/' + name}
def string(maximum=None, **kw): return {'type': 'string', **({'maxLength': maximum} if maximum else {}), **kw}
uuid = string(format='uuid')
integer = {'type': 'integer'}
boolean = {'type': 'boolean'}
def array(item, **kw): return {'type': 'array', 'items': item, **kw}
def obj(name, properties, required=None): spec['components']['schemas'][name] = {'type': 'object', 'additionalProperties': False, 'required': required or list(properties), 'properties': properties}
obj('PublicationPhotoInput', {'documentId':uuid, 'consentId':uuid, 'alt':string(300,minLength=1)})
obj('PublicationInput', {'projectId':{'type':['string','null'],'format':'uuid'}, 'slug':string(100,pattern='^[a-z0-9]+(?:-[a-z0-9]+)*$'), 'title':string(160,minLength=1), 'summary':string(3000,minLength=1), 'category':string(100,minLength=1), 'region':string(100), 'year':{'type':['integer','null'],'minimum':1900,'maximum':2200}, 'photos':array(ref('PublicationPhotoInput'),minItems=1,maxItems=12)}, ['slug','title','summary','category','region','photos'])
obj('PublicationAsset', {'id':uuid,'documentId':uuid,'consentId':uuid,'alt':string(300),'width':integer,'height':integer})
obj('Publication', {'id':uuid,'projectId':{'type':['string','null'],'format':'uuid'},'slug':string(100),'title':string(160),'summary':string(3000),'category':string(100),'region':string(100),'year':{'type':['integer','null']},'state':string(enum=['DRAFT','PUBLISHED','WITHDRAWN']),'version':integer,'photos':array(ref('PublicationAsset')),'publiclyAvailable':boolean})
obj('PublicationDecision', {'reason':string(1000,minLength=1),'privacyReviewed':{'type':'boolean','const':True}})
obj('PublicationReason', {'reason':string(1000,minLength=1)})
obj('PublicPhoto', {'url':string(),'alt':string(300),'width':integer,'height':integer})
obj('PublicProject', {'slug':string(100),'title':string(160),'summary':string(3000),'category':string(100),'region':string(100),'year':{'type':['integer','null']},'photos':array(ref('PublicPhoto'))})
obj('PublicProjectPage', {'items':array(ref('PublicProject')),'page':integer,'size':integer})
obj('SiteContentInput', {'heading':string(160,minLength=1),'introduction':string(2000,minLength=1),'about':string(4000,minLength=1),'services':array(string(160,minLength=1),minItems=1,maxItems=8)})
obj('SiteContent', {'heading':string(160),'introduction':string(2000),'about':string(4000),'services':array(string(160)),'state':string(enum=['DRAFT','APPROVED']),'version':integer})
obj('PublicSiteContent', {'approved':boolean,'heading':string(160),'introduction':string(2000),'about':string(4000),'services':array(string(160))})
obj('ContactInput', {'name':string(160,minLength=1),'email':string(254,format='email'),'message':string(3000,minLength=10),'noticeId':uuid,'privacyAccepted':{'type':'boolean','const':True},'website':string(200)})
obj('ContactReceipt', {'reference':uuid,'status':string(enum=['RECIBIDO'])})
obj('Contact', {'id':uuid,'name':string(160),'email':string(254),'message':string(3000),'noticeId':uuid,'receivedAt':string(format='date-time'),'deliveryState':string(enum=['PENDING','RUNNING','SENT','DEAD']),'attempts':integer,'errorCode':{'type':['string','null']}})
obj('OperationsStatus', {'failedJobs':integer,'failedContactNotifications':integer,'overdueScans':integer,'attentionRequired':boolean})
obj('ContactDelegate', {'userId':uuid,'active':boolean})
def param(name, where, schema, required=True): return {'name':name,'in':where,'required':required,'schema':schema}
key=param('Idempotency-Key','header',string(pattern='^[A-Za-z0-9._:-]{8,128}$'))
etag=param('If-Match','header',string(pattern='^"[1-9][0-9]*"$'))
page=[param('page','query',{'type':'integer','minimum':0,'maximum':100000},False),param('size','query',{'type':'integer','minimum':1,'maximum':100},False)]
errors={str(code):{'description':'Problem Details','content':{'application/problem+json':{'schema':ref('Problem')}}} for code in [400,401,403,404,409,412,413,415,422,428,429,500,503]}
def op(path, method, id, output, body=None, params=None, public=False, code='200'):
    operation={'operationId':id,'summary':id,'security':[] if public else ([{'cookieSession':[]},{'bearerAuth':[]}] if method=='get' else [{'cookieSession':[],'csrfHeader':[]},{'bearerAuth':[]}]),'parameters':params or [],'responses':{code:{'description':'Resultado; Cache-Control: no-store','content':{'application/json':{'schema':output}}},**errors}}
    if body: operation['requestBody']={'required':True,'content':{'application/json':{'schema':ref(body)}}}
    spec['paths'].setdefault(path,{})[method]=operation
op('/operations/status','get','operationsStatus',ref('OperationsStatus'))
op('/public/site','get','publicSite',ref('PublicSiteContent'),public=True)
op('/public/projects','get','publicProjects',ref('PublicProjectPage'),params=page,public=True)
op('/public/projects/{slug}','get','publicProject',ref('PublicProject'),params=[param('slug','path',string(100))],public=True)
op('/public/assets/{id}','get','publicAsset',string(format='binary'),params=[param('id','path',uuid)],public=True)
spec['paths']['/public/assets/{id}']['get']['responses']['200']['content']={'image/jpeg':{'schema':string(format='binary')}}
op('/public/privacy-notice','get','publicPrivacyNotice',ref('PrivacyNotice'),public=True)
spec['paths']['/public/privacy-notice']['get']['responses']['204']={'description':'Aviso aún no aprobado; sin cuerpo'}
op('/public/contact','post','publicContact',ref('ContactReceipt'),'ContactInput',[param('Idempotency-Key','header',uuid),param('Origin','header',string())],public=True,code='201')
spec['paths']['/public/contact']['post']['description']='Origen exacto, JSON ≤16 KiB, honeypot y límite PostgreSQL 8 solicitudes/10 min. La recepción no acredita correo ni lectura. Reintentos con el mismo UUID/body conservan referencia.'
op('/publications','get','listPublications',array(ref('Publication')),params=[param('projectId','query',uuid,False)])
op('/publications','post','draftPublication',ref('Publication'),'PublicationInput',[key],code='201')
op('/publications/{id}','get','readPublication',ref('Publication'),params=[param('id','path',uuid)])
op('/publications/{id}','put','editPublication',ref('Publication'),'PublicationInput',[param('id','path',uuid),key,etag])
for action,body in [('publish','PublicationDecision'),('withdraw','PublicationReason')]: op('/publications/{id}/'+action,'post',action+'Publication',ref('Publication'),body,[param('id','path',uuid),key,etag])
op('/publications/{id}/assets/{assetId}/preview','get','previewPublicationAsset',string(format='binary'),params=[param('id','path',uuid),param('assetId','path',uuid)])
spec['paths']['/publications/{id}/assets/{assetId}/preview']['get']['responses']['200']['content']={'image/jpeg':{'schema':string(format='binary')}}
op('/publication-site','get','siteContent',ref('SiteContent'))
op('/publication-site','put','editSiteContent',ref('SiteContent'),'SiteContentInput',[key,etag])
op('/publication-site/approve','post','approveSiteContent',ref('SiteContent'),params=[key,etag])
op('/publication-consents','get','publicationConsents',array(ref('Consent')),params=[param('projectId','query',uuid)])
op('/contacts','get','listContacts',array(ref('Contact')),params=page)
op('/contacts/delegates','put','delegateContacts',ref('ContactDelegate'),'ContactDelegate',[key])
op('/contacts/{id}/retry','post','retryContactNotification',ref('ContactReceipt'),params=[param('id','path',uuid),key])
obj('AccountSummary',{'id':uuid,'displayName':string(200),'email':string(254,format='email'),'roles':array(string(enum=['GERENTE','ADMIN','CLIENTE','PROVEEDOR','ASISTENTE_ADMINISTRATIVA'])),'enabled':boolean})
obj('AccountSearch',{'query':string(254,minLength=2),'role':{'type':['string','null'],'enum':['GERENTE','ADMIN','CLIENTE','PROVEEDOR','ASISTENTE_ADMINISTRATIVA',None]},'page':{'type':'integer','minimum':0,'maximum':100000},'size':{'type':'integer','minimum':1,'maximum':50}},['query'])
obj('AccountPage',{'items':array(ref('AccountSummary')),'page':integer,'size':integer,'hasMore':boolean})
obj('PartyMemberAccount',{'account':ref('AccountSummary'),'active':boolean})
obj('PartyMemberAccountPage',{'items':array(ref('PartyMemberAccount')),'page':integer,'size':integer,'hasMore':boolean})
obj('ProjectMemberAccount',{'account':ref('AccountSummary'),'capacity':string(enum=['CLIENT','OPERATIONS','SUPPLIER']),'supplierId':{'type':['string','null'],'format':'uuid'},'supplierName':{'type':['string','null']},'representative':boolean,'active':boolean})
obj('ProjectMemberAccountPage',{'items':array(ref('ProjectMemberAccount')),'page':integer,'size':integer,'hasMore':boolean})
account_page=[param('page','query',{'type':'integer','minimum':0,'maximum':100000},False),param('size','query',{'type':'integer','minimum':1,'maximum':50},False)]
search=param('query','query',string(254,minLength=2))
op('/accounts/search','post','searchAccounts',ref('AccountPage'),'AccountSearch')
spec['paths']['/accounts/search']['post']['description']='Solo GERENTE. Directorio privado de cuentas ATO registradas y activas, búsqueda literal por nombre/correo y rol; no consulta el IdP ni devuelve issuer/subject. Lectura auditada sin consulta personal en metadata.'
for collection in ['clients','suppliers']:
    op('/'+collection+'/{id}/member-accounts','get','list'+collection.title()+'MemberAccounts',ref('PartyMemberAccountPage'),params=[param('id','path',uuid),*account_page])
    spec['paths']['/'+collection+'/{id}/member-accounts']['get']['description']='Solo GERENTE. Identidades privadas de membresías de esta parte, incluidas revocadas/desactivadas. Concesión/revocación conserva PUT /members con ETag/idempotencia y contraseña reciente.'
op('/projects/{id}/member-accounts','get','listProjectMemberAccounts',ref('ProjectMemberAccountPage'),params=[param('id','path',uuid),*account_page])
op('/projects/{id}/account-candidates','post','projectAccountCandidates',ref('AccountPage'),'AccountSearch',params=[param('id','path',uuid),param('capacity','query',string(enum=['CLIENT','OPERATIONS','SUPPLIER']),False),param('supplierId','query',uuid,False)])
spec['paths']['/projects/{id}/account-candidates']['post']['description']='Solo GERENTE. CLIENT exige membresía activa de la cartera contratante; SUPPLIER requiere supplierId y cartera/rol de proveedor activos; OPERATIONS filtra asistente. El comando de concesión vuelve a validar cuenta/rol/membresías.'
(root / 'docs/openapi/m4.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n')
import runpy
runpy.run_path(str(root / 'scripts/generate-onboarding-openapi.py'))
runpy.run_path(str(root / 'scripts/generate-planning-openapi.py'))
