#!/usr/bin/env python3
"""Inventario revisable de artifacts locales; no publica ni incluye secretos."""
import hashlib
import json
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parent.parent
files = [root / 'target/atoconstrucciones-0.0.1-SNAPSHOT.jar', root / 'docs/openapi/m4.json', root / 'frontend/package-lock.json', root / 'frontend/.next/BUILD_ID']
assert all(p.is_file() for p in files), 'Construya Maven y Next antes de generar el manifiesto'
files += sorted((root / 'src/main/resources/db/migration').glob('*.sql'))
def tree_hash(folder):
    assert folder.is_dir(), f'Falta el artifact {folder}'
    digest = hashlib.sha256()
    paths = sorted(p for p in folder.rglob('*') if p.is_file())
    for path in paths:
        digest.update(str(path.relative_to(folder)).encode() + b'\0')
        digest.update(hashlib.sha256(path.read_bytes()).digest())
    return {'sha256': digest.hexdigest(), 'files': len(paths)}
directories = ['frontend/.next/standalone', 'frontend/.next/static', 'frontend/public']
report = {'milestone': 'M4', 'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(), 'workingTreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True)), 'artifacts': {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}, 'directories': {p: tree_hash(root / p) for p in directories}, 'deployment': 'pending', 'hosting': 'local + Cloudflare Tunnel', 'domain': 'atoconstrucciones.com', 'rpoHours': 24, 'rtoHours': 4}
(root / 'target/m4-release-manifest.json').write_text(json.dumps(report, indent=2) + '\n')
print('Manifiesto revisable creado en target/m4-release-manifest.json')
