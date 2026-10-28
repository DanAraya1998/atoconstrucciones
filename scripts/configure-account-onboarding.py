#!/usr/bin/env python3
"""Habilita altas/autorregistro en el Keycloak local existente, con copia privada previa."""
import argparse
import json
import os
from pathlib import Path
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--env-file', type=Path, default=root / '.env')
parser.add_argument('--apply', action='store_true', help='Aplicar configuración local; nunca importa ni reemplaza usuarios')
args = parser.parse_args()
values = dict(line.split('=', 1) for line in args.env_file.read_text().splitlines() if line and not line.startswith('#'))
issuer = values['ATO_OIDC_ISSUER_URI'].rstrip('/')
uri = urllib.parse.urlsplit(issuer)
if uri.scheme != 'http' or uri.hostname not in {'localhost', '127.0.0.1'} or uri.query or uri.fragment or uri.username or uri.password or '/realms/' not in uri.path:
    parser.error('Este comando solo configura un Keycloak HTTP de loopback. Producción requiere su propio procedimiento.')
base, realm = issuer.rsplit('/realms/', 1)
if not realm or '/' in realm:
    parser.error('Realm inválido')
if not args.apply:
    print('Preparado: habilitar registro/verificación, cliente de servicio de cuentas y retorno de activación. Use --apply para aplicar en el realm local.')
    sys.exit(0)
os.umask(0o077)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *unused): return None
http = urllib.request.build_opener(NoRedirect())
def request(url, method='GET', body=None, token=None, form=False):
    data = None if body is None else (urllib.parse.urlencode(body).encode() if form else json.dumps(body).encode())
    headers = {'Content-Type': 'application/x-www-form-urlencoded' if form else 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    try:
        with http.open(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=15) as response:
            raw = response.read(2_000_001)
            if len(raw) > 2_000_000: raise SystemExit('Respuesta de configuración demasiado grande')
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raise SystemExit(f'Configuración no completada: HTTP {error.code}; no se imprimen credenciales ni respuestas privadas') from None

token = request(base + '/realms/master/protocol/openid-connect/token', 'POST', {
    'grant_type': 'password', 'client_id': 'admin-cli', 'username': values.get('ATO_KEYCLOAK_ADMIN_USERNAME', 'local-admin'),
    'password': values['ATO_KEYCLOAK_ADMIN_PASSWORD']}, form=True)['access_token']
admin = base + '/admin/realms/' + urllib.parse.quote(realm, safe='')
def api(path, method='GET', body=None): return request(admin + path, method, body, token)
configuration = api('')
profile = api('/users/profile')
client_id = values.get('ATO_ACCOUNT_CLIENT_ID', 'ato-accounts')
existing = api('/clients?clientId=' + urllib.parse.quote(client_id, safe=''))
web_id = values.get('ATO_OIDC_CLIENT_ID', 'ato-web')
web = api('/clients?clientId=' + urllib.parse.quote(web_id, safe=''))
if len(web) != 1 or len(existing) > 1: raise SystemExit('Clientes ambiguos; se requiere revisión')
folder = root / '.m0-local'
folder.mkdir(exist_ok=True)
backup = folder / ('onboarding-config-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '.json')
backup.write_text(json.dumps({'realm': configuration, 'profile': profile, 'webClient': web[0], 'accountsClient': existing}, indent=2))
backup.chmod(0o600)
configuration.update({'registrationAllowed': True, 'verifyEmail': True, 'loginWithEmailAllowed': True, 'duplicateEmailsAllowed': False, 'registrationEmailAsUsername': True})
configuration.update({'internationalizationEnabled': True, 'defaultLocale': 'es', 'supportedLocales': list(dict.fromkeys(configuration.get('supportedLocales', []) + ['es', 'en']))})
if values.get('ATO_IDP_SMTP_HOST'):
    configuration['smtpServer'] = {key: values.get(env, default) for key, env, default in [
        ('host', 'ATO_IDP_SMTP_HOST', ''), ('port', 'ATO_IDP_SMTP_PORT', '587'), ('from', 'ATO_IDP_SMTP_FROM', ''),
        ('fromDisplayName', 'ATO_IDP_SMTP_FROM_NAME', 'ATO Construcciones'), ('auth', 'ATO_IDP_SMTP_AUTH', 'true'),
        ('user', 'ATO_IDP_SMTP_USER', ''), ('password', 'ATO_IDP_SMTP_PASSWORD', ''), ('ssl', 'ATO_IDP_SMTP_SSL', 'false'),
        ('starttls', 'ATO_IDP_SMTP_STARTTLS', 'true')]}
api('', 'PUT', configuration)
for name in ['atoProvisioningId', 'atoProvisioningHash']:
    attribute = {'name': name, 'displayName': name, 'validations': {'length': {'max': 100}}, 'permissions': {'view': ['admin'], 'edit': ['admin']}, 'multivalued': False}
    profile['attributes'] = [item for item in profile.get('attributes', []) if item['name'] != name] + [attribute]
api('/users/profile', 'PUT', profile)
secret = values.get('ATO_ACCOUNT_CLIENT_SECRET') or secrets.token_urlsafe(36)
client = {'clientId': client_id, 'enabled': True, 'protocol': 'openid-connect', 'publicClient': False,
          'serviceAccountsEnabled': True, 'standardFlowEnabled': False, 'directAccessGrantsEnabled': False, 'secret': secret}
if existing:
    client = {**existing[0], **client}
    api('/clients/' + existing[0]['id'], 'PUT', client)
else:
    api('/clients', 'POST', client)
client = api('/clients?clientId=' + urllib.parse.quote(client_id, safe=''))[0]
service = api('/clients/' + client['id'] + '/service-account-user')
management = api('/clients?clientId=realm-management')[0]
roles = [api('/clients/' + management['id'] + '/roles/' + role) for role in ['view-users', 'manage-users', 'query-users']]
api('/users/' + service['id'] + '/role-mappings/clients/' + management['id'], 'POST', roles)
callback = values['ATO_WEB_ORIGIN'].rstrip('/') + '/login'
web_client = web[0]
web_client['redirectUris'] = list(dict.fromkeys(web_client.get('redirectUris', []) + [callback]))
api('/clients/' + web_client['id'], 'PUT', web_client)
values.update({'ATO_ACCOUNT_PROVISIONING_ENABLED': 'true', 'ATO_ACCOUNT_CLIENT_ID': client_id, 'ATO_ACCOUNT_CLIENT_SECRET': secret})
args.env_file.write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
args.env_file.chmod(0o600)
print('Configurado el realm local, conservando sus usuarios. Copia privada en .m0-local/. Reinicie el backend con el archivo de entorno actualizado.')
print('SMTP del IdP ' + ('configurado; la entrega real requiere ensayo.' if configuration.get('smtpServer', {}).get('host') else 'pendiente: las activaciones mostrarán el fallo y podrán reintentarse.'))
