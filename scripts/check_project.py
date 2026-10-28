#!/usr/bin/env python3
"""Checks locales/CI: enlaces Markdown, contratos y secretos obvios. Sin dependencias."""
import json
from pathlib import Path
import re
import sys

EXCLUDED = {'.git', 'node_modules', '.next', 'target', '.idea', '.m0-local', 'test-results', 'playwright-report', '__pycache__'}
SECRET_PATTERNS = [
    re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    re.compile(r'AKIA[0-9A-Z]{16}'),
    re.compile(r'(?m)^ATO_[A-Z_]*(?:PASSWORD|SECRET)\s*=\s*[A-Za-z0-9_\-]{24,}\s*$'),
]

def check(root):
    errors = []
    for path in root.rglob('*'):
        if not path.is_file() or any(part in EXCLUDED for part in path.relative_to(root).parts):
            continue
        if path.suffix not in {'.md', '.py', '.java', '.ts', '.tsx', '.mjs', '.json', '.yaml', '.yml', '.properties', '.example'}:
            continue
        text = path.read_text()
        for pattern in SECRET_PATTERNS:
            if pattern.search(text): errors.append(f'Posible secreto: {path.relative_to(root)}')
        if path.suffix == '.md':
            for target in re.findall(r'(?<!!)\[[^\]]+\]\(([^)]+)\)', text):
                target = target.split(' "')[0].strip('<>')
                if re.match(r'^[a-zA-Z]+:', target) or target.startswith('#'): continue
                location = target.split('#')[0]
                if location and not (path.parent / location).exists(): errors.append(f'Enlace roto: {path.relative_to(root)} -> {target}')
    return errors

def validate_openapi(spec):
    assert spec['openapi'] == '3.1.1'
    operations = set()
    def visit(value):
        if isinstance(value, dict):
            if '$ref' in value:
                current = spec
                for part in value['$ref'].removeprefix('#/').split('/'): current = current[part]
            if 'operationId' in value:
                assert value['operationId'] not in operations
                operations.add(value['operationId'])
            for item in value.values(): visit(item)
        elif isinstance(value, list):
            for item in value: visit(item)
    visit(spec)
    assert len(operations) >= 18

def canonical(value):
    if isinstance(value, dict): return {key: canonical(item) for key, item in value.items() if key not in {'description', 'summary', 'example', 'examples'}}
    if isinstance(value, list): return [canonical(item) for item in value]
    return value

def compatible(old, new):
    # Conservador: preservar los campos/operaciones previos; permitir extensiones opcionales.
    def schema_matches(previous, current):
        previous, current = canonical(previous), canonical(current)
        if previous == current: return True
        if not isinstance(previous, dict) or not isinstance(current, dict): return False
        if 'properties' not in previous or 'properties' not in current: return False
        old_fields, new_fields = previous.pop('properties'), current.pop('properties')
        if previous != current: return False  # Incluye required, tipos, enums y constraints.
        if any(name in current.get('required', []) for name in new_fields.keys() - old_fields.keys()): return False
        return all(name in new_fields and schema_matches(field, new_fields[name]) for name, field in old_fields.items())

    def operation_matches(previous, current):
        if current is None: return False
        previous, current = canonical(previous), canonical(current)
        old_params, new_params = previous.pop('parameters', []), current.pop('parameters', [])
        if previous != current: return False
        by_key = {(p['in'], p['name']): p for p in new_params}
        if len(by_key) != len(new_params): return False
        if any(by_key.get((p['in'], p['name'])) != p for p in old_params): return False
        old_keys = {(p['in'], p['name']) for p in old_params}
        return all(p['in'] == 'query' and not p.get('required', False) for key, p in by_key.items() if key not in old_keys)

    for path, methods in old.get('paths', {}).items():
        for method, operation in methods.items():
            if not operation_matches(operation, new.get('paths', {}).get(path, {}).get(method)):
                return False
    for name, schema in old.get('components', {}).get('schemas', {}).items():
        if not schema_matches(schema, new.get('components', {}).get('schemas', {}).get(name)):
            return False
    return canonical(old.get('components', {}).get('securitySchemes')) == canonical(new.get('components', {}).get('securitySchemes'))

if __name__ == '__main__':
    root = Path(__file__).resolve().parent.parent
    failures = check(root)
    current = json.loads((root / 'docs/openapi/m4.json').read_text())
    validate_openapi(current)
    if len(sys.argv) == 3 and sys.argv[1] == '--baseline':
        if not compatible(json.loads(Path(sys.argv[2]).read_text()), current): failures.append('Cambio incompatible de contrato OpenAPI v1')
    for failure in failures: print(failure)
    if failures: raise SystemExit(1)
    print('Enlaces, contratos y secretos: OK')
