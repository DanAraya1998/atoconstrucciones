#!/usr/bin/env python3
"""Arranca el backend de pruebas de altas sobre una DB nueva y un IdP aislado."""
import argparse
import os
from pathlib import Path

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--env-file', type=Path, default=root / '.m0-local/onboarding-isolated/test.env')
parser.add_argument('--classpath', type=Path, default=root / 'target/m2-test-classpath.txt')
parser.add_argument('--log-file', type=Path)
args = parser.parse_args()
env = os.environ.copy()
values = dict(line.split('=', 1) for line in args.env_file.read_text().splitlines() if line and not line.startswith('#'))
if values.get('ATO_OIDC_ISSUER_URI') != 'http://localhost:8182/realms/ato-onboarding' or values.get('ATO_WEB_ORIGIN') != 'http://localhost:3302':
    parser.error('Este harness solo admite el realm y el origen aislados de pruebas')
env.update(values)
env['ATO_ONBOARDING_BROWSER_HARNESS'] = '1'
classpath = f'{root}/target/test-classes:{root}/target/classes:' + args.classpath.read_text().strip()
os.chdir(root)
if args.log_file:
    log = args.log_file.open('w')
    os.dup2(log.fileno(), 1)
    os.dup2(log.fileno(), 2)
os.execvpe('java', ['java', '-Dspring.devtools.restart.enabled=false', '-cp', classpath, 'com.daraya.atoconstrucciones.AccountOnboardingBrowserHarness'], env)
