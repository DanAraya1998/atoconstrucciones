#!/usr/bin/env python3
"""Genera solo fixtures locales, sin descargar ni escribir secretos en Git."""
import json
import os
from pathlib import Path
import secrets
import sys

root = Path(__file__).resolve().parent.parent
if (root / '.env').exists() and '--refresh-realm' not in sys.argv:
    raise SystemExit('.env ya existe; se conserva. Consulte README.md para regenerar fixtures.')
os.umask(0o077)
local = root / '.m0-local'
local.mkdir(exist_ok=True)
client_secret = secrets.token_urlsafe(36)
values = {
    'SPRING_PROFILES_ACTIVE': 'dev',
    'ATO_DB_URL': 'jdbc:postgresql://127.0.0.1:54329/ato',
    'ATO_DB_USER': 'ato',
    'ATO_DB_PASSWORD': secrets.token_urlsafe(32),
    'ATO_KEYCLOAK_ADMIN_PASSWORD': secrets.token_urlsafe(32),
    'ATO_OIDC_ISSUER_URI': 'http://localhost:8180/realms/ato',
    'ATO_OIDC_CLIENT_ID': 'ato-web',
    'ATO_OIDC_CLIENT_SECRET': client_secret,
    'ATO_REQUIRE_MFA': 'false',
    'ATO_CONTACT_RATE_SECRET': secrets.token_urlsafe(32),
    'ATO_PUBLIC_ORIGIN': 'https://atoconstrucciones.com',
    'ATO_MAIL_ENABLED': 'false',
    'ATO_WEB_ORIGIN': 'http://localhost:3000',
    'ATO_BOOTSTRAP_MANAGER_EMAIL': 'gerente@example.invalid',
}
if '--refresh-realm' in sys.argv and (root / '.env').exists():
    values.update(dict(line.split('=', 1) for line in (root / '.env').read_text().splitlines() if line and not line.startswith('#')))
    client_secret = values['ATO_OIDC_CLIENT_SECRET']
(root / '.env').write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
users = []
credentials = []
existing_accounts = {}
if '--refresh-realm' in sys.argv and (local / 'accounts.txt').exists():
    existing_accounts = dict(line.split(': ', 1) for line in (local / 'accounts.txt').read_text().splitlines())
for name in ('gerente', 'admin', 'asistente', 'cliente-a', 'cliente-b', 'proveedor'):
    password = existing_accounts.get(f'{name}@example.invalid') or secrets.token_urlsafe(20)
    users.append({'username': name, 'email': f'{name}@example.invalid', 'emailVerified': True,
                  'enabled': True, 'firstName': name, 'lastName': 'Fixture',
                  'requiredActions': ['CONFIGURE_TOTP'] if values['ATO_REQUIRE_MFA'] == 'true' else [],
                  'credentials': [{'type': 'password', 'value': password, 'temporary': False}]})
    credentials.append(f'{name}@example.invalid: {password}')
realm = {
    'realm': 'ato', 'enabled': True, 'registrationAllowed': True, 'verifyEmail': True,
    'loginTheme': 'ato', 'displayName': 'ATO Construcciones',
    'loginWithEmailAllowed': True, 'duplicateEmailsAllowed': False, 'registrationEmailAsUsername': True,
    'resetPasswordAllowed': True, 'bruteForceProtected': True,
    'sslRequired': 'external', 'internationalizationEnabled': True,
    'supportedLocales': ['es', 'en'], 'defaultLocale': 'es',
    'clients': [{'clientId': 'ato-web', 'enabled': True, 'publicClient': False,
                 'baseUrl': 'http://localhost:3000/',
                 'secret': client_secret, 'standardFlowEnabled': True,
                 'directAccessGrantsEnabled': False,
                 'redirectUris': ['http://localhost:3000/login/oauth2/code/ato'],
                 'webOrigins': ['http://localhost:3000'],
                 'attributes': {'pkce.code.challenge.method': 'S256'},
                 'protocolMappers': [{'name': 'api-audience', 'protocol': 'openid-connect',
                    'protocolMapper': 'oidc-audience-mapper', 'config': {
                        'included.custom.audience': 'ato-api', 'access.token.claim': 'true',
                        'id.token.claim': 'false'}}]}],
    'users': users,
    'browserFlow': 'ato-browser',
    'authenticationFlows': [{'alias': 'ato-browser', 'description': 'M0: contraseña; MFA configurable',
        'providerId': 'basic-flow', 'topLevel': True, 'builtIn': False,
        'authenticationExecutions': [
            {'authenticator': 'auth-username-password-form', 'authenticatorFlow': False,
             'requirement': 'REQUIRED', 'priority': 10, 'authenticatorConfig': 'ato-amr-password'},
            {'authenticator': 'auth-otp-form', 'authenticatorFlow': False,
             'requirement': 'REQUIRED' if values['ATO_REQUIRE_MFA'] == 'true' else 'DISABLED', 'priority': 20, 'authenticatorConfig': 'ato-amr-otp'},
        ]}],
    'authenticatorConfig': [
        {'alias': 'ato-amr-password', 'config': {'default.reference.value': 'pwd', 'default.reference.maxAge': '300'}},
        {'alias': 'ato-amr-otp', 'config': {'default.reference.value': 'otp', 'default.reference.maxAge': '300'}},
    ],
}
realm['clients'][0]['protocolMappers'].append({
    'name': 'authentication-method', 'protocol': 'openid-connect', 'protocolMapper': 'oidc-amr-mapper',
    'config': {'id.token.claim': 'true', 'access.token.claim': 'true'},
})
(local / 'ato-realm.json').write_text(json.dumps(realm, indent=2))
(local / 'accounts.txt').write_text('\n'.join(credentials) + '\n')
print('Generados .env, .m0-local/ato-realm.json y .m0-local/accounts.txt (permisos 600).')
