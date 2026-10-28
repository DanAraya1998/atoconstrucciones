#!/usr/bin/env python3
"""Dump/restore del harness M2–M4; conserva copias y no elimina bases."""
import argparse
import json
from pathlib import Path
import subprocess
import uuid
import time
import hashlib
import secrets
import os

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--milestone', choices=['m2', 'm3', 'm4'], default='m2')
parser.add_argument('--docker', action='store_true', help='Usar las herramientas PostgreSQL 18.6 del contenedor en CI')
args = parser.parse_args()
port = str(json.loads(Path(f'/tmp/ato-{args.milestone}-browser-db.json').read_text())['port'])
base = ['-h', '127.0.0.1', '-p', port, '-U', 'postgres']
folder = root / 'target' / (args.milestone + '-restore-' + uuid.uuid4().hex[:10])
folder.mkdir(parents=True, mode=0o700)
database = args.milestone + '_restore_' + uuid.uuid4().hex[:10]
command_prefix = ['docker', 'run', '--rm', '--network', 'host', '--volume', f'{folder}:{folder}', 'postgres:18.6'] if args.docker else []

def sql(db, query):
    return subprocess.run(command_prefix + ['psql', *base, '-d', db, '-X', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-c', query], check=True, capture_output=True, text=True).stdout.strip()

tables = ['user_account', 'project', 'contract', 'contract_amendment', 'document', 'document_link', 'fiscal_voucher', 'fiscal_voucher_document', 'expense', 'expense_allocation', 'expense_revision', 'cost_recognition', 'treasury_account', 'treasury_movement', 'treasury_effect', 'collection_application', 'payment_application', 'deposit_report', 'financial_state_event', 'audit_event', 'idempotency_record']
if args.milestone in {'m3', 'm4'}:
    tables += ['schedule', 'schedule_task', 'task_assignment', 'task_dependency', 'progress_report', 'progress_verification', 'progress_baseline', 'task_weight', 'progress_baseline_approval', 'schedule_revision']
if args.milestone == 'm4':
    # Todos los datos/ACL de la aplicación, incluidos consentimientos y revisiones.
    tables = sql('postgres', "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").splitlines()
    tables = [t for t in tables if t != 'flyway_schema_history']
started = time.monotonic()
# El dump tiene su snapshot consistente. Detener actividad del navegador antes de este ensayo.
before = {table: sql('postgres', f"SELECT count(*)||':'||coalesce(md5(string_agg(v::text,',' ORDER BY v::text)),'empty') FROM (SELECT to_jsonb(t) v FROM {table} t) q") for table in tables}
if args.milestone in {'m3', 'm4'}:
    required = ['project', 'document', 'deposit_report', 'schedule', 'schedule_task', 'task_assignment', 'task_dependency', 'progress_report', 'progress_verification', 'progress_baseline', 'task_weight', 'progress_baseline_approval', 'schedule_revision']
    assert all(int(before[table].split(':')[0]) > 0 for table in required), 'El ensayo M3 requiere los datos de los flujos E2E; no acepta tablas esenciales vacías'
subprocess.run(command_prefix + ['pg_dump', *base, '-d', 'postgres', '-F', 'c', '-f', str(folder / 'database.dump')], check=True, capture_output=True)
sql('postgres', f'CREATE DATABASE {database}')
subprocess.run(command_prefix + ['pg_restore', *base, '-d', database, '--exit-on-error', str(folder / 'database.dump')], check=True, capture_output=True)
after = {table: sql(database, f"SELECT count(*)||':'||coalesce(md5(string_agg(v::text,',' ORDER BY v::text)),'empty') FROM (SELECT to_jsonb(t) v FROM {table} t) q") for table in tables}
assert before == after, 'La restauración difiere de las fuentes sintéticas'
migrations = int(sql(database, 'SELECT count(*) FROM flyway_schema_history WHERE success'))
assert migrations >= (8 if args.milestone == 'm4' else 7 if args.milestone == 'm3' else 6)
objects_verified = 0
if args.milestone == 'm4':
    # Escrow separado del directorio de copias. Clave sintética nueva, nunca en Git.
    key_file = root / 'target' / ('m4-escrow-' + uuid.uuid4().hex[:10] + '.key')
    key_file.write_text(secrets.token_hex(32))
    key_file.chmod(0o600)
    classpath = str(root / 'target/classes') + ':' + (root / 'target/m2-test-classpath.txt').read_text().strip()
    def crypt(operation, source, destination):
        subprocess.run(['java', '-Xmx1g', '-cp', classpath, 'com.daraya.atoconstrucciones.documents.RecoveryTool', operation, str(source), str(destination), str(key_file)], check=True, capture_output=True)
    object_folder = folder / 'restored-objects'
    object_folder.mkdir(mode=0o700)
    references = json.loads(sql(database, "SELECT coalesce(json_agg(q),'[]') FROM (SELECT object_key,object_version,expected_size AS size,expected_sha256 AS sha256,media_type FROM document WHERE object_version IS NOT NULL UNION SELECT object_key,object_version,size,sha256,'image/jpeg' FROM document_variant) q"))
    assert references, 'M4 restore requires non-empty original and derivative files'
    manifest = []
    for index, ref in enumerate(references):
        name = hashlib.sha256(ref['object_key'].encode()).hexdigest() + '-' + ref['object_version'] + '.bin'
        source = root / 'target/m4-browser-objects' / name
        assert source.stat().st_size == ref['size'] and hashlib.sha256(source.read_bytes()).hexdigest() == ref['sha256']
        encrypted = folder / f'object-{index:06d}.ato'
        crypt('encrypt', source, encrypted)
        restored = object_folder / name
        crypt('decrypt', encrypted, restored)
        assert restored.stat().st_size == ref['size'] and hashlib.sha256(restored.read_bytes()).hexdigest() == ref['sha256']
        manifest.append({'file': encrypted.name, 'key': ref['object_key'], 'version': ref['object_version'], 'size': ref['size'], 'sha256': ref['sha256'], 'mediaType': ref['media_type']})
        objects_verified += 1
    (folder / 'object-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    crypt('encrypt', folder / 'object-manifest.json', folder / 'manifest.ato')
    crypt('encrypt', folder / 'database.dump', folder / 'database.ato')
    crypt('decrypt', folder / 'database.ato', folder / 'recovered-database.dump')
    assert hashlib.sha256((folder / 'database.dump').read_bytes()).digest() == hashlib.sha256((folder / 'recovered-database.dump').read_bytes()).digest()
    crypt('verify', folder, Path('-'))
    # Tokens web restaurados no se reutilizan. Las ACL y roles se conservaron en before==after.
    sql(database, 'DELETE FROM spring_session')
    (root / 'target/m4-restored-environment.json').write_text(json.dumps({'port': int(port), 'database': database, 'objectsFolder': str(object_folder)}) + '\n')
result = {'database': database, 'tablesVerified': len(tables), 'migrations': migrations, 'rows': {table: int(value.split(':')[0]) for table, value in after.items()}, 'objectsVerified': objects_verified, 'elapsedSeconds': round(time.monotonic() - started, 3), 'scope': 'PostgreSQL + archivos y clave sintéticos; cuenta Drive/IdP productivos pendientes' if args.milestone == 'm4' else 'PostgreSQL sintético; objetos Drive/IdP fuera de este ensayo'}
(folder / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, ensure_ascii=False))
