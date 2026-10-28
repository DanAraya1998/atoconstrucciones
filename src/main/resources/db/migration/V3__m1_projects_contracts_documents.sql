-- Expansión M1; no modifica las migraciones de fundamentos.
CREATE TABLE party (
 id uuid PRIMARY KEY, kind varchar(12) NOT NULL CHECK (kind IN ('CLIENT','SUPPLIER')),
 name varchar(160) NOT NULL, email varchar(254) NOT NULL, phone varchar(24) NOT NULL,
 category varchar(100) NOT NULL DEFAULT '', active boolean NOT NULL DEFAULT true,
 version bigint NOT NULL DEFAULT 1, created_by uuid NOT NULL REFERENCES user_account(id), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX party_kind_idx ON party(kind,id);
CREATE TABLE party_member (
 party_id uuid NOT NULL REFERENCES party(id), user_id uuid NOT NULL REFERENCES user_account(id),
 active boolean NOT NULL, PRIMARY KEY(party_id,user_id)
);
CREATE TABLE project (
 id uuid PRIMARY KEY, code varchar(30) NOT NULL UNIQUE, name varchar(160) NOT NULL,
 client_id uuid NOT NULL REFERENCES party(id), location varchar(500) NOT NULL,
 latitude numeric(10,7), longitude numeric(10,7), contact_email varchar(254) NOT NULL,
 starts_on date NOT NULL, estimated_ends_on date NOT NULL,
 state varchar(20) NOT NULL DEFAULT 'BORRADOR' CHECK(state IN ('BORRADOR','ACTIVO','SUSPENDIDO','CANCELADO','EN_CIERRE','CERRADO')),
 version bigint NOT NULL DEFAULT 1, created_by uuid NOT NULL REFERENCES user_account(id), created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(starts_on<=estimated_ends_on), CHECK(latitude BETWEEN -90 AND 90), CHECK(longitude BETWEEN -180 AND 180),
 CHECK((latitude IS NULL)=(longitude IS NULL))
);
CREATE TABLE project_member (
 project_id uuid NOT NULL REFERENCES project(id), user_id uuid NOT NULL REFERENCES user_account(id),
 capacity varchar(12) NOT NULL CHECK(capacity IN ('CLIENT','OPERATIONS','SUPPLIER')),
 supplier_id uuid REFERENCES party(id), representative boolean NOT NULL DEFAULT false, active boolean NOT NULL,
 PRIMARY KEY(project_id,user_id,capacity), CHECK((capacity='SUPPLIER')=(supplier_id IS NOT NULL)),
 CHECK(NOT representative OR capacity='CLIENT')
);
CREATE INDEX project_members_user_idx ON project_member(user_id,project_id) WHERE active;
CREATE TABLE document (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES project(id), owner_id uuid NOT NULL REFERENCES user_account(id),
 filename varchar(160) NOT NULL, media_type varchar(80) NOT NULL, expected_size bigint NOT NULL CHECK(expected_size>0 AND expected_size<=20971520),
 expected_sha256 char(64) NOT NULL, object_key varchar(200) NOT NULL UNIQUE, object_version varchar(200),
 state varchar(20) NOT NULL DEFAULT 'RESERVED' CHECK(state IN ('RESERVED','QUARANTINED','CLEAN','INFECTED','REJECTED','EXPIRED')),
 audience varchar(20) NOT NULL DEFAULT 'INTERNAL' CHECK(audience IN ('INTERNAL','CLIENT','SUPPLIER')),
 recipient_id uuid REFERENCES user_account(id), document_class varchar(20) NOT NULL CHECK(document_class IN ('CONTRACT','EVIDENCE','PHOTO')),
 expires_at timestamptz NOT NULL DEFAULT now()+interval '1 hour', created_at timestamptz NOT NULL DEFAULT now(),
 scan_attempts integer NOT NULL DEFAULT 0, scan_after timestamptz NOT NULL DEFAULT now(), scan_lease uuid, scan_until timestamptz,
 scan_error varchar(50), scanned_at timestamptz, version bigint NOT NULL DEFAULT 1,
 CHECK((audience='SUPPLIER')=(recipient_id IS NOT NULL))
);
CREATE INDEX document_scan_idx ON document(scan_after) WHERE state='QUARANTINED';
CREATE TABLE document_link (
 document_id uuid NOT NULL REFERENCES document(id), project_id uuid NOT NULL REFERENCES project(id),
 purpose varchar(40) NOT NULL, created_by uuid NOT NULL REFERENCES user_account(id), created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(document_id,purpose)
);
CREATE TABLE document_hold (
 document_id uuid PRIMARY KEY REFERENCES document(id), reason varchar(500) NOT NULL,
 actor_id uuid NOT NULL REFERENCES user_account(id), recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE contract (
 project_id uuid PRIMARY KEY REFERENCES project(id), number varchar(80) NOT NULL UNIQUE,
 net_amount numeric(20,2) NOT NULL CHECK(net_amount>=0), tax_amount numeric(20,2) NOT NULL CHECK(tax_amount>=0),
 currency char(3) NOT NULL CHECK(currency='CRC'), starts_on date NOT NULL, ends_on date NOT NULL,
 scope varchar(2000) NOT NULL, state varchar(15) NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','FORMALIZED')),
 evidence_id uuid REFERENCES document(id), formalized_by uuid REFERENCES user_account(id), formalized_at timestamptz,
 version bigint NOT NULL DEFAULT 1, CHECK(starts_on<=ends_on),
 CHECK((state='FORMALIZED')=(formalized_at IS NOT NULL))
);
CREATE FUNCTION protect_formalized_contract() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF OLD.state='FORMALIZED' THEN RAISE EXCEPTION 'Formalized base is immutable'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER immutable_base BEFORE UPDATE OR DELETE ON contract FOR EACH ROW EXECUTE FUNCTION protect_formalized_contract();
CREATE TABLE extra_request (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES project(id), requested_by uuid NOT NULL REFERENCES user_account(id),
 origin varchar(20) NOT NULL CHECK(origin IN ('CLIENT','MANAGER','ON_BEHALF')),
 scope varchar(2000) NOT NULL, state varchar(25) NOT NULL DEFAULT 'SOLICITADO'
 CHECK(state IN ('SOLICITADO','PENDIENTE_APROBACION','APROBADO','EN_EJECUCION','TERMINADO','RECHAZADO','CANCELADO')),
 current_revision integer NOT NULL DEFAULT 0, version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE extra_revision (
 extra_id uuid NOT NULL REFERENCES extra_request(id), revision integer NOT NULL,
 scope varchar(2000) NOT NULL, net_amount numeric(20,2) NOT NULL CHECK(net_amount>=0), tax_amount numeric(20,2) NOT NULL CHECK(tax_amount>=0),
 currency char(3) NOT NULL CHECK(currency='CRC'), days_delta integer NOT NULL CHECK(days_delta BETWEEN -3650 AND 3650),
 authored_by uuid NOT NULL REFERENCES user_account(id), created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(extra_id,revision)
);
CREATE TABLE extra_acceptance (
 extra_id uuid NOT NULL, revision integer NOT NULL, actor_id uuid NOT NULL REFERENCES user_account(id),
 evidence_id uuid NOT NULL REFERENCES document(id), accepted_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(extra_id,revision), FOREIGN KEY(extra_id,revision) REFERENCES extra_revision(extra_id,revision)
);
CREATE TABLE contract_amendment (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES contract(project_id), extra_id uuid REFERENCES extra_request(id), revision integer,
 kind varchar(15) NOT NULL CHECK(kind IN ('EXTRA','CANCELLATION','ADJUSTMENT')),
 net_delta numeric(20,2) NOT NULL, tax_delta numeric(20,2) NOT NULL, days_delta integer NOT NULL,
 evidence_id uuid NOT NULL REFERENCES document(id), reason varchar(1000) NOT NULL,
 reversal_of uuid UNIQUE REFERENCES contract_amendment(id), actor_id uuid NOT NULL REFERENCES user_account(id),
 effective_at timestamptz NOT NULL DEFAULT now(), UNIQUE(extra_id,revision,kind)
);
CREATE TRIGGER immutable_amendment BEFORE UPDATE OR DELETE ON contract_amendment FOR EACH ROW EXECUTE FUNCTION reject_audit_change();
CREATE TRIGGER immutable_extra_revision BEFORE UPDATE OR DELETE ON extra_revision FOR EACH ROW EXECUTE FUNCTION reject_audit_change();
CREATE TRIGGER immutable_extra_acceptance BEFORE UPDATE OR DELETE ON extra_acceptance FOR EACH ROW EXECUTE FUNCTION reject_audit_change();
CREATE TABLE privacy_notice (
 id uuid PRIMARY KEY, revision integer NOT NULL UNIQUE, state varchar(12) NOT NULL CHECK(state IN ('DRAFT','APPROVED')),
 controller_name varchar(200) NOT NULL, contact_email varchar(254) NOT NULL, content varchar(10000) NOT NULL,
 approved_by uuid REFERENCES user_account(id), approved_at timestamptz,
 CHECK((state='APPROVED')=(approved_at IS NOT NULL))
);
CREATE TABLE publication_consent (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES project(id), document_id uuid NOT NULL REFERENCES document(id),
 notice_id uuid NOT NULL REFERENCES privacy_notice(id), evidence_id uuid NOT NULL REFERENCES document(id), granted_by uuid NOT NULL REFERENCES user_account(id),
 purpose varchar(500) NOT NULL, channels varchar(500) NOT NULL, expires_on date NOT NULL,
 granted_at timestamptz NOT NULL DEFAULT now(), withdrawn_at timestamptz, withdrawal_reason varchar(500), version bigint NOT NULL DEFAULT 1
);
CREATE TABLE privacy_request (
 id uuid PRIMARY KEY, actor_id uuid NOT NULL REFERENCES user_account(id), kind varchar(20) NOT NULL CHECK(kind IN ('ACCESS','RECTIFICATION','WITHDRAWAL')),
 details varchar(2000) NOT NULL, state varchar(15) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','RESOLVED')),
 resolution varchar(2000), resolved_by uuid REFERENCES user_account(id), created_at timestamptz NOT NULL DEFAULT now()
);
