CREATE TABLE user_account (
    id uuid PRIMARY KEY, issuer text NOT NULL, subject text NOT NULL,
    email text NOT NULL, enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(), UNIQUE (issuer, subject)
);
CREATE TABLE user_role (
    user_id uuid REFERENCES user_account(id),
    role varchar(40) NOT NULL CHECK (role IN ('ADMIN','GERENTE','ASISTENTE_ADMINISTRATIVA','PROVEEDOR','CLIENTE')),
    PRIMARY KEY (user_id, role)
);
CREATE TABLE resource_membership (
    resource_id uuid NOT NULL, user_id uuid NOT NULL REFERENCES user_account(id),
    revoked_at timestamptz, PRIMARY KEY (resource_id, user_id)
);
CREATE TABLE invitation (
    id uuid PRIMARY KEY, email text NOT NULL, role varchar(40) NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE, expires_at timestamptz NOT NULL,
    created_by uuid NOT NULL REFERENCES user_account(id), accepted_by uuid REFERENCES user_account(id),
    accepted_at timestamptz,
    CHECK (role IN ('ADMIN','ASISTENTE_ADMINISTRATIVA','PROVEEDOR','CLIENTE'))
);
CREATE TABLE support_request (
    id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES user_account(id),
    resource_id uuid NOT NULL, purpose varchar(500) NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT now(), approved_by uuid REFERENCES user_account(id),
    expires_at timestamptz, revoked_at timestamptz,
    CHECK (approved_by IS NULL OR approved_by <> user_id)
);
CREATE TABLE audit_event (
    id uuid PRIMARY KEY, actor_id uuid NOT NULL REFERENCES user_account(id),
    action varchar(100) NOT NULL, resource_id uuid NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(), trace_id varchar(36) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX audit_resource_idx ON audit_event (resource_id, recorded_at);
CREATE FUNCTION reject_audit_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Audit is append-only'; END; $$;
CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_change();
CREATE TABLE idempotency_record (
    actor_id uuid NOT NULL REFERENCES user_account(id), operation varchar(200) NOT NULL,
    key varchar(128) NOT NULL, request_hash varchar(64) NOT NULL, response jsonb,
    created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY (actor_id, operation, key)
);
CREATE TABLE foundation_resource (
    id uuid PRIMARY KEY, name varchar(120) NOT NULL, version bigint NOT NULL DEFAULT 1,
    created_by uuid NOT NULL REFERENCES user_account(id), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE outbox_job (
    id uuid PRIMARY KEY, actor_id uuid NOT NULL REFERENCES user_account(id),
    resource_id uuid NOT NULL REFERENCES foundation_resource(id), kind varchar(80) NOT NULL,
    state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','RUNNING','SUCCEEDED','DEAD')),
    attempts integer NOT NULL DEFAULT 0, max_attempts integer NOT NULL CHECK (max_attempts > 0),
    available_at timestamptz NOT NULL DEFAULT now(), lease_until timestamptz,
    lease_token uuid, error_code varchar(80), created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz
);
CREATE INDEX jobs_ready_idx ON outbox_job (available_at, created_at) WHERE state IN ('PENDING','RUNNING');
CREATE TABLE foundation_receipt (
    job_id uuid PRIMARY KEY REFERENCES outbox_job(id), resource_id uuid NOT NULL REFERENCES foundation_resource(id),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Esquema Spring Session JDBC PostgreSQL, gestionado por Flyway.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL, SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL, LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL, EXPIRY_TIME BIGINT NOT NULL, PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);
CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL, ATTRIBUTE_NAME VARCHAR(200) NOT NULL, ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
