-- Clasificación expansiva: conserva íntegro el texto libre anterior y las membresías.
ALTER TABLE party ADD COLUMN supplier_category varchar(30);
UPDATE party SET supplier_category='OTHER' WHERE kind='SUPPLIER';
ALTER TABLE party ADD CONSTRAINT party_supplier_category_check CHECK (
    (kind='CLIENT' AND supplier_category IS NULL) OR
    (kind='SUPPLIER' AND supplier_category IS NOT NULL AND supplier_category IN
        ('MASTER_BUILDER','ELECTRICIAN','PLUMBER','GLAZIER','MACHINERY','FINISHES','HARDWARE_STORE','OTHER'))
);
CREATE INDEX party_supplier_category_idx ON party(supplier_category,id) WHERE kind='SUPPLIER';
