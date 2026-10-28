#!/usr/bin/env python3
"""Copia cifrada/restore de cuentas de un harness activo; rechaza datos empresariales reales."""
import argparse
import hashlib
import json
import secrets
import subprocess
import time
import uuid
from pathlib import Path

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--synthetic', action='store_true', required=True)
parser.add_argument('--onboarding', action='store_true', help='DB aislada de altas/aprobación V10')
args = parser.parse_args()
port = str(json.loads((root / '.m0-local/onboarding-isolated/database.json' if args.onboarding else Path('/tmp/ato-m4-browser-db.json')).read_text())['port'])
base = ['-h', '127.0.0.1', '-p', port, '-U', 'postgres']
def sql(db, query):
    return subprocess.run(['psql', *base, '-d', db, '-X', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-c', query], check=True, capture_output=True, text=True).stdout.strip()

assert sql('postgres', "SELECT count(*) FROM user_account WHERE email NOT LIKE '%@example.invalid'") == '0', 'Solo se permiten identidades sintéticas'
assert sql('postgres', "SELECT count(*) FROM party WHERE email NOT LIKE '%@example.invalid'") == '0', 'Solo se permite cartera sintética'
assert sql('postgres', "SELECT count(*) FROM invitation WHERE email NOT LIKE '%@example.invalid'") == '0', 'Solo se permiten invitaciones sintéticas'
required = ['user_account', 'user_role', 'party', 'party_member', 'project', 'project_member', 'account_activation' if args.onboarding else 'invitation']
assert all(int(sql('postgres', f'SELECT count(*) FROM {t}')) > 0 for t in required), 'Ejecute primero el flujo E2E de cuentas'
assert int(sql('postgres', 'SELECT count(*) FROM flyway_schema_history WHERE success')) >= (10 if args.onboarding else 9)
assert int(sql('postgres', "SELECT count(*) FROM user_account WHERE display_name<>''")) > 0, 'Se requieren nombres del login OIDC'
if args.onboarding:
    assert int(sql('postgres', 'SELECT count(*) FROM user_account WHERE NOT enabled')) > 0, 'Se requiere una suspensión conservada'
else:
    assert int(sql('postgres', 'SELECT count(*) FROM project_member WHERE NOT active')) > 0, 'Se requiere revocación conservada'
folder = root / 'target' / ('accounts-restore-' + uuid.uuid4().hex[:10])
folder.mkdir(mode=0o700)
key = root / 'target' / ('accounts-escrow-' + uuid.uuid4().hex[:10] + '.key')
key.write_text(secrets.token_hex(32)); key.chmod(0o600)
classpath = str(root / 'target/classes') + ':' + (root / 'target/m2-test-classpath.txt').read_text().strip()
def crypt(operation, source, destination):
    subprocess.run(['java', '-cp', classpath, 'com.daraya.atoconstrucciones.documents.RecoveryTool', operation, str(source), str(destination), str(key)], check=True, capture_output=True)

tables = sql('postgres', "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").splitlines()
def snapshot(db):
    return {t: sql(db, f"SELECT count(*)||':'||coalesce(md5(string_agg(v::text,',' ORDER BY v::text)),'empty') FROM (SELECT to_jsonb(t) v FROM {t} t) q") for t in tables}
started = time.monotonic()
before = snapshot('postgres')
subprocess.run(['pg_dump', *base, '-d', 'postgres', '-F', 'c', '-f', str(folder / 'database.dump')], check=True, capture_output=True)
crypt('encrypt', folder / 'database.dump', folder / 'database.ato')
crypt('decrypt', folder / 'database.ato', folder / 'recovered.dump')
assert hashlib.sha256((folder / 'database.dump').read_bytes()).digest() == hashlib.sha256((folder / 'recovered.dump').read_bytes()).digest()
database = 'accounts_restore_' + uuid.uuid4().hex[:10]
sql('postgres', f'CREATE DATABASE {database}')
subprocess.run(['pg_restore', *base, '-d', database, '--exit-on-error', str(folder / 'recovered.dump')], check=True, capture_output=True)
after = snapshot(database)
assert before == after, 'El restore difiere del origen'
sql(database, 'DELETE FROM spring_session')
configuration = {'port': int(port), 'database': database, 'objectsFolder': str(root / 'target/m4-browser-objects')}
(root / ('target/onboarding-restored-environment.json' if args.onboarding else 'target/m4-restored-environment.json')).write_text(json.dumps(configuration) + '\n')
report = {'tablesVerified': len(tables), 'migrations': int(sql(database, 'SELECT count(*) FROM flyway_schema_history WHERE success')), 'rows': {t: int(v.split(':')[0]) for t, v in after.items()}, 'elapsedSeconds': round(time.monotonic() - started, 3), 'scope': 'DB/ACL/nombres/estados/activaciones sintéticos; objetos e IdP fuera de este ensayo'}
(folder / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
