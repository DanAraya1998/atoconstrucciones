#!/usr/bin/env python3
"""Harness aislado opt-in de navegador: PostgreSQL/Keycloak reales, archivos controlados."""
import argparse
import os
from urllib.parse import urlsplit
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--classpath', type=Path, default=Path('target/m2-test-classpath.txt'))
parser.add_argument('--milestone', choices=['m2', 'm3', 'm4'], default='m2')
parser.add_argument('--web-origin', default='http://localhost:3000', help='Origen loopback del frontend aislado')
args = parser.parse_args()
origin = urlsplit(args.web_origin)
if origin.scheme != 'http' or origin.hostname not in {'localhost', '127.0.0.1'} or origin.path or origin.query or origin.fragment or origin.username or origin.password:
    parser.error('--web-origin debe ser un origen HTTP de loopback')
root = Path(__file__).resolve().parent.parent
env = os.environ.copy()
if (root / '.env').exists():
    for line in (root / '.env').read_text().splitlines():
        if line and not line.startswith('#'):
            key, value = line.split('=', 1)
            env.setdefault(key, value)
env['ATO_M2_BROWSER_HARNESS'] = '1'
env['ATO_BROWSER_MILESTONE'] = args.milestone
env['ATO_DB_PASSWORD'] = 'synthetic'
env['ATO_WEB_ORIGIN'] = args.web_origin
env['ATO_BOOTSTRAP_MANAGER_EMAIL'] = 'gerente@example.invalid'
classpath_file = args.classpath if args.classpath.is_absolute() else root / args.classpath
classpath = f'{root}/target/test-classes:{root}/target/classes:' + classpath_file.read_text().strip()
os.chdir(root)
os.execvpe('java', ['java', '-Dspring.devtools.restart.enabled=false', '-cp', classpath, 'com.daraya.atoconstrucciones.M2BrowserHarness'], env)
