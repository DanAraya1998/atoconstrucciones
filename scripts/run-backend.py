#!/usr/bin/env python3
"""Carga solo .env local sin imprimir valores y ejecuta Spring Boot."""
import os
from pathlib import Path
import sys

root = Path(__file__).resolve().parent.parent
env = os.environ.copy()
if (root / '.env').exists():
    for line in (root / '.env').read_text().splitlines():
        if line and not line.startswith('#'):
            key, value = line.split('=', 1)
            env.setdefault(key, value)
os.chdir(root)
if len(sys.argv) > 1 and sys.argv[1] == '--jar':
    command = ['java', '-jar', str(root / 'target/atoconstrucciones-0.0.1-SNAPSHOT.jar')]
else:
    command = ['./mvnw', 'spring-boot:run']
os.execvpe(command[0], command, env)
