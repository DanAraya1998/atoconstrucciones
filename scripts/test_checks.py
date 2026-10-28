import copy
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from check_project import check, compatible, validate_openapi

class PipelineChecks(unittest.TestCase):
    def test_broken_link_fails(self):
        with TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'example.md').write_text('[referencia](missing.md)')
            self.assertTrue(check(root))

    def test_secret_fixture_fails(self):
        with TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'example.properties').write_text('ATO_DB_PASSWORD=' + 'a' * 32)
            self.assertTrue(check(root))

    def test_removed_route_and_schema_change_fail(self):
        spec = json.loads((Path(__file__).resolve().parent.parent / 'docs/openapi/m0.json').read_text())
        validate_openapi(spec)
        removed = copy.deepcopy(spec)
        del removed['paths']['/session']
        self.assertFalse(compatible(spec, removed))
        changed = copy.deepcopy(spec)
        changed['components']['schemas']['Money']['properties']['amount']['type'] = 'number'
        self.assertFalse(compatible(spec, changed))
        self.assertTrue(compatible(spec, copy.deepcopy(spec)))

    def test_optional_extensions_pass_but_new_requirements_and_enum_changes_fail(self):
        spec = json.loads((Path(__file__).resolve().parent.parent / 'docs/openapi/m4.json').read_text())
        extended = copy.deepcopy(spec)
        extended['components']['schemas']['PartyInput']['properties']['optionalDetail'] = {'type': 'string'}
        extended['paths']['/suppliers']['get']['parameters'].append({'in': 'query', 'name': 'optionalFilter', 'required': False, 'schema': {'type': 'string'}})
        self.assertTrue(compatible(spec, extended))
        required = copy.deepcopy(extended)
        required['components']['schemas']['PartyInput']['required'].append('optionalDetail')
        self.assertFalse(compatible(spec, required))
        required = copy.deepcopy(extended)
        required['paths']['/suppliers']['get']['parameters'][-1]['required'] = True
        self.assertFalse(compatible(spec, required))
        changed = copy.deepcopy(spec)
        changed['components']['schemas']['SupplierCategory']['enum'] = ['OTHER']
        self.assertFalse(compatible(spec, changed))
        removed = copy.deepcopy(spec)
        del removed['components']['schemas']['PartyInput']['properties']['email']
        self.assertFalse(compatible(spec, removed))

if __name__ == '__main__': unittest.main()
