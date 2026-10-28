#!/usr/bin/env python3
"""Extensión expansiva de cuentas sobre M4; conserva los contratos previos."""
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
spec = json.loads((root / 'docs/openapi/m4.json').read_text())
spec['info']['version'] = '0.4.2'
schemas = spec['components']['schemas']
def ref(name): return {'$ref': '#/components/schemas/' + name}
def string(**kwargs): return {'type': 'string', **kwargs}
uuid = string(format='uuid')
integer = {'type': 'integer', 'minimum': 1}
boolean = {'type': 'boolean'}
def obj(name, props, required=None):
    schemas[name] = {'type': 'object', 'additionalProperties': False, 'properties': props, 'required': list(props) if required is None else required}
obj('AccountProjectGrant', {'projectId': uuid, 'version': integer, 'representative': boolean}, ['projectId', 'version'])
obj('AccountAccessInput', {'role': string(enum=['CLIENTE', 'PROVEEDOR', 'ASISTENTE_ADMINISTRATIVA']), 'partyId': {'type': ['string', 'null'], 'format': 'uuid'}, 'partyVersion': {'type': ['integer', 'null'], 'minimum': 1}, 'projects': {'type': 'array', 'items': ref('AccountProjectGrant'), 'maxItems': 100}}, ['role', 'projects'])
obj('DirectAccountInput', {'displayName': string(minLength=1, maxLength=200), 'email': string(format='email', maxLength=254), 'access': ref('AccountAccessInput')})
obj('AccountDecision', {'reason': string(minLength=1, maxLength=500)})
obj('ManagedAccount', {'id': uuid, 'displayName': string(maxLength=200), 'email': string(format='email'), 'roles': {'type': 'array', 'items': string()}, 'state': string(enum=['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED']), 'emailVerified': boolean, 'version': integer, 'activationState': string(enum=['NONE', 'PENDING', 'SENDING', 'SENT', 'FAILED']), 'createdAt': string(format='date-time')})
obj('ManagedAccountPage', {'items': {'type': 'array', 'items': ref('ManagedAccount')}, 'page': {'type': 'integer'}, 'size': {'type': 'integer'}, 'hasMore': boolean})
obj('AccountManagementSearch', {'query': string(maxLength=254), 'state': string(enum=['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED']), 'page': {'type': 'integer', 'minimum': 0, 'maximum': 100000}, 'size': {'type': 'integer', 'minimum': 1, 'maximum': 50}}, ['query'])
obj('AccountCapabilities', {'directAvailable': boolean})
def param(name, where, schema): return {'name': name, 'in': where, 'required': True, 'schema': schema}
key = param('Idempotency-Key', 'header', string(pattern='^[A-Za-z0-9._:-]{8,128}$'))
etag = param('If-Match', 'header', string(pattern='^"[1-9][0-9]*"$'))
target = param('id', 'path', uuid)
def op(path, method, name, output, body=None, params=None, code='200', description='Solo GERENTE. Mutaciones con contraseña reciente, idempotencia, auditoría y control de versión.'):
    operation = {'operationId': name, 'summary': name, 'description': description,
                 'security': [{'cookieSession': []}, {'bearerAuth': []}] if method == 'get' else [{'cookieSession': [], 'csrfHeader': []}, {'bearerAuth': []}],
                 'parameters': params or [], 'responses': {code: {'description': 'Resultado privado; Cache-Control: no-store; ETag de la cuenta en respuestas individuales.', 'content': {'application/json': {'schema': ref(output)}}}}}
    for status in [400, 401, 403, 404, 409, 412, 422, 428, 500, 502, 503]:
        operation['responses'][str(status)] = {'description': 'Problem Details', 'content': {'application/problem+json': {'schema': ref('Problem')}}}
    if body: operation['requestBody'] = {'required': True, 'content': {'application/json': {'schema': ref(body)}}}
    spec['paths'].setdefault(path, {})[method] = operation
op('/accounts/me', 'get', 'myAccountAccessState', 'ManagedAccount', description='Cualquier identidad activa consulta exclusivamente el estado de su propia cuenta. Las cuentas pendientes/rechazadas no reciben roles efectivos ni acceso empresarial.')
op('/accounts/capabilities', 'get', 'accountCapabilities', 'AccountCapabilities')
op('/accounts/management-search', 'post', 'manageAccountsSearch', 'ManagedAccountPage', 'AccountManagementSearch', description='Directorio privado de GERENTE, incluidas cuentas pendientes/rechazadas/suspendidas. Búsqueda literal en cuerpo; no se guarda la consulta en auditoría.')
op('/accounts/{id}', 'get', 'managedAccount', 'ManagedAccount', params=[target])
op('/accounts', 'post', 'createDirectAccount', 'ManagedAccount', 'DirectAccountInput', [key], '201', 'GERENTE con contraseña reciente. Crea identidad nueva en Keycloak, aprueba perfil/cartera/obras seleccionadas y encola activación. Nunca fusiona cuentas por correo. El correo pendiente/fallido es independiente de la aprobación; no se almacenan contraseñas ni enlaces. Fallo remoto: 502/503; cuenta existente: 409. Reintento remoto correlacionado por referencia y hash.')
op('/accounts/{id}/approve', 'post', 'approveAccountAccess', 'ManagedAccount', 'AccountAccessInput', [target, key, etag], description='GERENTE con contraseña reciente. Aprueba o amplía permisos de una cuenta con correo verificado. Perfil, cartera, obras y auditoría se confirman en una transacción. Valida versiones de cuenta/cartera/obras; representante contractual se confirma por obra. Excluye cuentas GERENTE y concesión de ADMIN/GERENTE.')
for action in ['reject', 'suspend', 'resume']:
    op('/accounts/{id}/' + action, 'post', action + 'Account', 'ManagedAccount', 'AccountDecision', [target, key, etag])
op('/accounts/{id}/activation', 'post', 'retryAccountActivation', 'ManagedAccount', params=[target, key, etag])
(root / 'docs/openapi/m4.json').write_text(json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
