-- Expansiva: las cuentas con permisos existentes conservan su acceso.
ALTER TABLE user_account ADD COLUMN access_state varchar(20) NOT NULL DEFAULT 'APPROVED'
    CHECK (access_state IN ('PENDING','APPROVED','REJECTED'));
ALTER TABLE user_account ADD COLUMN access_version bigint NOT NULL DEFAULT 1;
ALTER TABLE user_account ADD COLUMN email_verified boolean NOT NULL DEFAULT true;
ALTER TABLE user_account ADD COLUMN decided_by uuid REFERENCES user_account(id);
ALTER TABLE user_account ADD COLUMN decided_at timestamptz;
ALTER TABLE user_account ADD COLUMN decision_note varchar(500) NOT NULL DEFAULT '';
UPDATE user_account u SET access_state='PENDING'
    WHERE NOT EXISTS (SELECT 1 FROM user_role r WHERE r.user_id=u.id);
CREATE INDEX account_review_idx ON user_account(access_state,created_at,id);

-- El envío se confirma aparte de la concesión local; no se guardan claves ni enlaces.
CREATE TABLE account_activation (
    user_id uuid PRIMARY KEY REFERENCES user_account(id),
    state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','SENDING','SENT','FAILED')),
    attempts integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT now(),
    lease_until timestamptz, lease_token uuid,
    error_code varchar(80), sent_at timestamptz
);
