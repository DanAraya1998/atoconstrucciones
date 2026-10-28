#!/usr/bin/env python3
"""Arranca solo smoke sobre DB/objetos restaurados y sintéticos. No toca base empresarial."""
import json
import os
from pathlib import Path
root = Path(__file__).resolve().parent.parent
env = os.environ.copy()
for line in (root / '.env').read_text().splitlines():
    if line and not line.startswith('#'):
        key, value = line.split('=', 1)
        env.setdefault(key, value)
configuration = root / 'target/m4-restored-environment.json'
data = json.loads(configuration.read_text())
env.update(ATO_M2_BROWSER_HARNESS='1', ATO_BROWSER_MILESTONE='m4', ATO_BROWSER_RESTORE_ENV=str(configuration), ATO_BROWSER_OBJECTS_FOLDER=data['objectsFolder'], ATO_DB_PASSWORD='synthetic', ATO_WEB_ORIGIN='http://localhost:3000')
classpath = f'{root}/target/test-classes:{root}/target/classes:' + (root / 'target/m2-test-classpath.txt').read_text().strip()
os.chdir(root)
os.execvpe('java', ['java', '-Dspring.devtools.restart.enabled=false', '-cp', classpath, 'com.daraya.atoconstrucciones.M4RestoredHarness'], env)
