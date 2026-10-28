#!/usr/bin/env python3
"""Activa el tema ATO en un realm local existente, sin reimportar usuarios."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--env-file', type=Path, default=root / '.env')
parser.add_argument('--apply', action='store_true')
parser.add_argument('--theme', choices=['ato', 'keycloak.v2'], default='ato', help='keycloak.v2 permite volver al tema anterior estándar')
args = parser.parse_args()
values = dict(line.split('=', 1) for line in args.env_file.read_text().splitlines() if line and not line.startswith('#'))
issuer = values['ATO_OIDC_ISSUER_URI'].rstrip('/')
uri = urllib.parse.urlsplit(issuer)
if uri.scheme != 'http' or uri.hostname not in {'localhost', '127.0.0.1'} or uri.query or uri.fragment or uri.username or uri.password or '/realms/' not in uri.path:
    parser.error('Solo se admite un Keycloak HTTP de loopback; producción tiene su propio procedimiento')
base, realm = issuer.rsplit('/realms/', 1)
if not realm or '/' in realm or realm == 'master':
    parser.error('Realm de aplicación inválido')
origin = values['ATO_WEB_ORIGIN'].rstrip('/')
web = urllib.parse.urlsplit(origin)
if web.scheme != 'http' or web.hostname not in {'localhost', '127.0.0.1'} or web.path or web.query or web.fragment or web.username or web.password:
    parser.error('El sitio de retorno debe ser un origen HTTP de loopback')
if not args.apply:
    print('Preparado: seleccionar tema ' + args.theme + ' y retorno al sitio local. Monte primero infra/keycloak/themes/ato y use --apply. No se cambia el emisor ni los usuarios.')
    raise SystemExit(0)
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
            if len(raw) > 2_000_000: raise SystemExit('Respuesta demasiado grande')
            return json.loads(raw) if raw else None
    except (urllib.error.URLError, TimeoutError) as error:
        code = error.code if isinstance(error, urllib.error.HTTPError) else 'conexión'
        raise SystemExit(f'No se completó la configuración ({code}); no se imprimen respuestas privadas') from None

token = request(base + '/realms/master/protocol/openid-connect/token', 'POST', {
    'grant_type': 'password', 'client_id': 'admin-cli',
    'username': values.get('ATO_KEYCLOAK_ADMIN_USERNAME', 'local-admin'),
    'password': values['ATO_KEYCLOAK_ADMIN_PASSWORD']}, form=True)['access_token']
themes = request(base + '/admin/serverinfo', token=token)['themes']['login']
if args.theme not in {theme['name'] for theme in themes}:
    raise SystemExit('El tema no está instalado. Monte el directorio y reinicie Keycloak antes de aplicar; no se cambió el realm.')
admin = base + '/admin/realms/' + urllib.parse.quote(realm, safe='')
configuration = request(admin, token=token)
client_id = values.get('ATO_OIDC_CLIENT_ID', 'ato-web')
clients = request(admin + '/clients?clientId=' + urllib.parse.quote(client_id, safe=''), token=token)
if len(clients) != 1: raise SystemExit('Cliente web ambiguo o ausente; no se cambió el realm')
client = clients[0]
folder = root / '.m0-local'
folder.mkdir(exist_ok=True)
backup = folder / ('theme-config-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '.json')
backup.write_text(json.dumps({'issuer': issuer, 'clientId': client_id, 'loginTheme': configuration.get('loginTheme'), 'baseUrl': client.get('baseUrl')}, indent=2))
backup.chmod(0o600)
previous_base_url = client.get('baseUrl', '')
client['baseUrl'] = origin + '/'
client_url = admin + '/clients/' + urllib.parse.quote(client['id'], safe='')
request(client_url, 'PUT', client, token)
try:
    request(admin, 'PUT', {'loginTheme': args.theme}, token)
except SystemExit:
    client['baseUrl'] = previous_base_url
    request(client_url, 'PUT', client, token)
    raise
print('Tema ' + args.theme + ' seleccionado y retorno al sitio configurado. Emisor, usuarios, credenciales y permisos conservados. Copia privada en .m0-local/.')
