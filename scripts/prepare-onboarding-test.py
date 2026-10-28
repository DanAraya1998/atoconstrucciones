#!/usr/bin/env python3
"""Prepara fixtures/standalone aislados; conserva el realm y frontend habituales."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--keycloak-home', type=Path)
parser.add_argument('--frontend-only', action='store_true')
args = parser.parse_args()
os.umask(0o077)
local = root / '.m0-local/onboarding-isolated'
if not args.frontend_only:
    if args.keycloak_home is None: parser.error('--keycloak-home es obligatorio para preparar el realm')
    if (local / 'test.env').exists(): parser.error('El entorno aislado ya existe; se conserva. --frontend-only actualiza solo el standalone.')
    realm = json.loads((root / '.m0-local/ato-realm.json').read_text())
    if any(not user.get('email', '').endswith('@example.invalid') for user in realm.get('users', [])):
        parser.error('El realm fuente debe contener únicamente fixtures example.invalid')
    local.mkdir(parents=True, exist_ok=True)
    secret = secrets.token_urlsafe(36)
    realm.update(realm='ato-onboarding', loginTheme='ato', displayName='ATO Construcciones', registrationAllowed=True, verifyEmail=True, registrationEmailAsUsername=True, loginWithEmailAllowed=True, internationalizationEnabled=True, defaultLocale='es', supportedLocales=['es', 'en'])
    client = next(client for client in realm['clients'] if client['clientId'] == 'ato-web')
    client.update(secret=secret, baseUrl='http://localhost:3302/', redirectUris=['http://localhost:3302/login/oauth2/code/ato'], webOrigins=['http://localhost:3302'])
    realm['clients'] = [client]
    destination = args.keycloak_home / 'data/import'
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'ato-onboarding-realm.json').write_text(json.dumps(realm))
    (destination / 'ato-onboarding-realm.json').chmod(0o600)
    shutil.copytree(root / 'infra/keycloak/themes/ato', args.keycloak_home / 'themes/ato', dirs_exist_ok=True)
    values = {'ATO_OIDC_ISSUER_URI': 'http://localhost:8182/realms/ato-onboarding', 'ATO_OIDC_CLIENT_ID': 'ato-web', 'ATO_OIDC_CLIENT_SECRET': secret,
              'ATO_WEB_ORIGIN': 'http://localhost:3302', 'ATO_KEYCLOAK_ADMIN_PASSWORD': secrets.token_urlsafe(36), 'ATO_DB_PASSWORD': 'synthetic',
              'ATO_BOOTSTRAP_MANAGER_EMAIL': 'gerente@example.invalid', 'ATO_REQUIRE_MFA': 'false', 'ATO_MAIL_ENABLED': 'false',
              'ATO_IDP_SMTP_HOST': '127.0.0.1', 'ATO_IDP_SMTP_PORT': '2527', 'ATO_IDP_SMTP_FROM': 'ato@example.invalid',
              'ATO_IDP_SMTP_AUTH': 'false', 'ATO_IDP_SMTP_STARTTLS': 'false', 'ATO_IDP_SMTP_SSL': 'false'}
    (local / 'test.env').write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
source = root / 'frontend'
frontend = root / 'target/onboarding-frontend'
shutil.copytree(source, frontend, dirs_exist_ok=True, ignore=shutil.ignore_patterns('node_modules', '.next*', '.env*', 'test-results', 'playwright-report'))
modules = frontend / 'node_modules'
if modules.is_symlink(): modules.unlink()
if modules.exists(): shutil.rmtree(modules)
shutil.copytree(source / 'node_modules', modules, symlinks=True)
(frontend / '.env.local').write_text('ATO_BACKEND_ORIGIN=http://127.0.0.1:9194\n')
print('Preparado el frontend aislado en target/onboarding-frontend; compilar con npm run build --prefix target/onboarding-frontend.')
