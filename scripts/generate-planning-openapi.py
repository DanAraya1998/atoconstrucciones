#!/usr/bin/env python3
"""Extensión compatible: categoría principal de proveedores; fechas conservan el contrato M3."""
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
path = root / 'docs/openapi/m4.json'
spec = json.loads(path.read_text())
spec['info']['version'] = '0.4.3'
schemas = spec['components']['schemas']
schemas['SupplierCategory'] = {'type': 'string', 'enum': [
    'MASTER_BUILDER', 'ELECTRICIAN', 'PLUMBER', 'GLAZIER', 'MACHINERY', 'FINISHES', 'HARDWARE_STORE', 'OTHER']}
category = {'$ref': '#/components/schemas/SupplierCategory'}
schemas['PartyInput']['properties']['supplierCategory'] = {
    'anyOf': [category, {'type': 'null'}],
    'description': 'Categoría principal, solo SUPPLIER. Omitir/null: OTHER al crear, conserva la categoría vigente al editar. category conserva el detalle libre histórico.'}
schemas['Party']['properties']['supplierCategory'] = category
listing = spec['paths']['/suppliers']['get']
listing['parameters'] = [p for p in listing.get('parameters', []) if p['name'] != 'supplierCategory'] + [
    {'name': 'supplierCategory', 'in': 'query', 'required': False, 'schema': category,
     'description': 'Filtro de categoría, aplicado antes de paginar y dentro del alcance autorizado.'}]
spec['paths']['/suppliers']['post']['description'] = 'Solo GERENTE. Registra un proveedor en cartera sin cuenta de usuario ni obra. No crea membresías ni concede permisos. Categoría principal controlada; detalle libre conservado.'
spec['paths']['/suppliers/{id}']['patch']['description'] = 'GERENTE o asistente autorizada por obra. If-Match e idempotencia. Cambia categoría/contacto sin alterar asignaciones o permisos. Omitir supplierCategory conserva su valor.'
spec['paths']['/projects/{projectId}/schedule/changes']['post']['description'] = 'GERENTE u OPERATIONS vigente. Reprograma hojas/hitos y cambia duración en un lote completo: revisión, If-Match, motivo e idempotencia. Fechas civiles: endExclusive exclusivo, hito con inicio=fin. Valida FS/lag y DAG global; no desplaza sucesoras automáticamente. Historial y auditoría atómicos; 412 conserva el borrador para revisión. Cambiar fechas invalida la cobertura de pesos aprobada y no modifica el contrato firmado.'
path.write_text(json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
