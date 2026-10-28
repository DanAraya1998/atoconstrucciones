#!/usr/bin/env python3
"""Copia el frontend a un artefacto aislado para ensayar la presentación pública."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parent.parent
source = root / 'frontend'
destination = root / 'target/landing-frontend'
shutil.copytree(source, destination, dirs_exist_ok=True, ignore=shutil.ignore_patterns('node_modules', '.next*', '.env*', 'test-results', 'playwright-report'))
modules = destination / 'node_modules'
if not modules.exists():
    shutil.copytree(source / 'node_modules', modules, symlinks=True)
(destination / '.env.local').write_text('ATO_BACKEND_ORIGIN=http://127.0.0.1:9355\n')
print('Frontend de prueba preparado en target/landing-frontend; backend sintético 9355, servidor Next 3345.')
