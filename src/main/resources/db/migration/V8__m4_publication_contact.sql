-- Expansiva: conserva V1–V7 y los originales privados en Drive.
CREATE TABLE public_site_content (
 id integer PRIMARY KEY CHECK (id=1), heading varchar(160) NOT NULL DEFAULT '',
 introduction varchar(2000) NOT NULL DEFAULT '', about varchar(4000) NOT NULL DEFAULT '',
 services jsonb NOT NULL DEFAULT '[]', state varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','APPROVED')),
 version bigint NOT NULL DEFAULT 1, approved_by uuid REFERENCES user_account(id), approved_at timestamptz
);
INSERT INTO public_site_content(id) VALUES (1);
CREATE TABLE document_variant (
 id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES document(id),
 object_key text NOT NULL UNIQUE, object_version text NOT NULL,
 sha256 varchar(64) NOT NULL, size bigint NOT NULL CHECK(size BETWEEN 1 AND 5242880),
 width integer NOT NULL CHECK(width BETWEEN 1 AND 1600), height integer NOT NULL CHECK(height BETWEEN 1 AND 1600),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(id,document_id)
);
CREATE TRIGGER immutable_variant BEFORE UPDATE OR DELETE ON document_variant FOR EACH ROW EXECUTE FUNCTION reject_audit_change();
CREATE TABLE publication (
 id uuid PRIMARY KEY, project_id uuid REFERENCES project(id), slug varchar(100) NOT NULL UNIQUE,
 title varchar(160) NOT NULL, summary varchar(3000) NOT NULL, category varchar(100) NOT NULL,
 region varchar(100) NOT NULL, year integer CHECK(year BETWEEN 1900 AND 2200),
 state varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','PUBLISHED','WITHDRAWN')),
 version bigint NOT NULL DEFAULT 1, created_by uuid NOT NULL REFERENCES user_account(id),
 approved_by uuid REFERENCES user_account(id), published_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE published_asset (
 id uuid PRIMARY KEY, publication_id uuid NOT NULL REFERENCES publication(id),
 document_id uuid NOT NULL REFERENCES document(id), variant_id uuid NOT NULL,
 consent_id uuid NOT NULL REFERENCES publication_consent(id), alt varchar(300) NOT NULL,
 position integer NOT NULL CHECK(position BETWEEN 0 AND 11),
 FOREIGN KEY(variant_id,document_id) REFERENCES document_variant(id,document_id),
 UNIQUE(publication_id,position), UNIQUE(publication_id,document_id)
);
CREATE TABLE publication_revision (
 id uuid PRIMARY KEY, publication_id uuid NOT NULL REFERENCES publication(id), version bigint NOT NULL,
 action varchar(30) NOT NULL, snapshot jsonb NOT NULL, reason varchar(1000) NOT NULL,
 actor_id uuid NOT NULL REFERENCES user_account(id), recorded_at timestamptz NOT NULL DEFAULT now(), UNIQUE(publication_id,version)
);
CREATE TRIGGER immutable_publication_history BEFORE UPDATE OR DELETE ON publication_revision FOR EACH ROW EXECUTE FUNCTION reject_audit_change();
CREATE TABLE contact_request (
 id uuid PRIMARY KEY, key_hash varchar(64) NOT NULL UNIQUE, body_hash varchar(64) NOT NULL,
 name varchar(160) NOT NULL, email varchar(254) NOT NULL, message varchar(3000) NOT NULL,
 notice_id uuid NOT NULL REFERENCES privacy_notice(id), received_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE contact_delivery (
 contact_id uuid PRIMARY KEY REFERENCES contact_request(id), state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','RUNNING','SENT','DEAD')),
 attempts integer NOT NULL DEFAULT 0, available_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid, lease_until timestamptz, error_code varchar(80), sent_at timestamptz
);
CREATE INDEX contact_delivery_ready ON contact_delivery(available_at) WHERE state IN ('PENDING','RUNNING');
CREATE TABLE contact_rate_limit (
 fingerprint varchar(64) NOT NULL, bucket bigint NOT NULL, attempts integer NOT NULL,
 PRIMARY KEY(fingerprint,bucket)
);
CREATE TABLE contact_access_delegate (
 user_id uuid PRIMARY KEY REFERENCES user_account(id), active boolean NOT NULL,
 granted_by uuid NOT NULL REFERENCES user_account(id), granted_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX published_asset_consent ON published_asset(consent_id);
