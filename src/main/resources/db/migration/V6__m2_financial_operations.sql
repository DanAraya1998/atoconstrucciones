CREATE TABLE treasury_account (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE,
    kind varchar(10) NOT NULL CHECK (kind IN ('BANK','CASH')),
    currency char(3) NOT NULL DEFAULT 'CRC' CHECK (currency='CRC'),
    created_by uuid NOT NULL REFERENCES user_account(id)
);

CREATE TABLE fiscal_voucher (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES project(id),
    fiscal_key varchar(50) NOT NULL UNIQUE,
    sha256 char(64) NOT NULL,
    document_id uuid NOT NULL REFERENCES document(id),
    voucher_type varchar(50) NOT NULL,
    currency char(3) NOT NULL,
    net_amount numeric(23,5) NOT NULL CHECK (net_amount>=0),
    tax_amount numeric(23,5) NOT NULL CHECK (tax_amount>=0),
    gross_amount numeric(23,5) NOT NULL CHECK (gross_amount>0),
    reference_key varchar(50),
    extraction jsonb NOT NULL,
    bundle_hash char(64) NOT NULL,
    imported_by uuid NOT NULL REFERENCES user_account(id),
    imported_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE fiscal_voucher_document (
    voucher_id uuid NOT NULL REFERENCES fiscal_voucher(id),
    document_id uuid NOT NULL REFERENCES document(id),
    project_id uuid NOT NULL REFERENCES project(id),
    linked_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(voucher_id,document_id)
);
CREATE TABLE expense (
    id uuid PRIMARY KEY,
    supplier_id uuid REFERENCES party(id),
    supplier_exception varchar(1000) NOT NULL DEFAULT '',
    accrued_on date NOT NULL,
    category varchar(60) NOT NULL,
    description varchar(1000) NOT NULL,
    currency char(3) NOT NULL CHECK (currency='CRC'),
    net_amount numeric(23,5) NOT NULL CHECK (net_amount>=0),
    tax_amount numeric(23,5) NOT NULL CHECK (tax_amount>=0),
    gross_amount numeric(23,5) NOT NULL CHECK (gross_amount>0 AND gross_amount=net_amount+tax_amount),
    voucher_id uuid UNIQUE REFERENCES fiscal_voucher(id),
    fiscal_key varchar(50) UNIQUE,
    original_expense_id uuid REFERENCES expense(id),
    allocation_revision integer NOT NULL DEFAULT 1,
    recognition_sign smallint NOT NULL DEFAULT 1 CHECK (recognition_sign IN (-1,1)),
    difference_reason varchar(1000) NOT NULL DEFAULT '',
    evidence_id uuid REFERENCES document(id),
    state varchar(20) NOT NULL DEFAULT 'REGISTERED' CHECK (state IN ('REGISTERED','IN_REVIEW','VALIDATED','REJECTED','REVERSED')),
    version bigint NOT NULL DEFAULT 1,
    created_by uuid NOT NULL REFERENCES user_account(id),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    decided_at timestamptz,
    decision_reason varchar(1000)
);
CREATE TABLE expense_allocation (
    id uuid PRIMARY KEY,
    expense_id uuid NOT NULL REFERENCES expense(id),
    project_id uuid NOT NULL REFERENCES project(id),
    extra_id uuid REFERENCES extra_request(id),
    allocation_revision integer NOT NULL DEFAULT 1,
    net_amount numeric(23,5) NOT NULL CHECK (net_amount>=0),
    tax_amount numeric(23,5) NOT NULL CHECK (tax_amount>=0),
    gross_amount numeric(23,5) NOT NULL CHECK (gross_amount>0 AND gross_amount=net_amount+tax_amount),
    UNIQUE(expense_id,project_id,extra_id,allocation_revision)
);
CREATE TABLE expense_revision (
    expense_id uuid NOT NULL REFERENCES expense(id),
    revision integer NOT NULL,
    input jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    actor_id uuid NOT NULL REFERENCES user_account(id),
    PRIMARY KEY(expense_id,revision)
);
CREATE INDEX expense_allocation_project_idx ON expense_allocation(project_id,expense_id);
CREATE TABLE cost_recognition (
    id uuid PRIMARY KEY,
    expense_id uuid NOT NULL REFERENCES expense(id),
    allocation_id uuid NOT NULL REFERENCES expense_allocation(id),
    project_id uuid NOT NULL REFERENCES project(id),
    amount numeric(23,5) NOT NULL CHECK (amount<>0),
    net_amount numeric(23,5) NOT NULL,
    tax_amount numeric(23,5) NOT NULL,
    original_id uuid REFERENCES cost_recognition(id),
    effective_on date NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    actor_id uuid NOT NULL REFERENCES user_account(id),
    reason varchar(1000) NOT NULL,
    CHECK(amount=net_amount+tax_amount)
);
CREATE INDEX cost_recognition_project_idx ON cost_recognition(project_id,recorded_at);

CREATE TABLE treasury_movement (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES project(id),
    account_id uuid NOT NULL REFERENCES treasury_account(id),
    kind varchar(30) NOT NULL CHECK (kind IN ('RECEIPT','PAYMENT','ADVANCE','SUPPLIER_REFUND','CUSTOMER_REFUND','FINANCING','OTHER_OUT','TRANSFER_IN','TRANSFER_OUT')),
    supplier_id uuid REFERENCES party(id),
    money_amount numeric(20,2) NOT NULL CHECK (money_amount>0),
    currency char(3) NOT NULL CHECK (currency='CRC'),
    operation_on date NOT NULL,
    payment_method varchar(30) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER','CASH','CARD','OTHER')),
    bank_reference varchar(160) NOT NULL DEFAULT '',
    fact_key varchar(160) NOT NULL,
    source_movement_id uuid REFERENCES treasury_movement(id),
    transfer_id uuid,
    evidence_id uuid REFERENCES document(id),
    exception_reason varchar(1000) NOT NULL DEFAULT '',
    state varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT','CONFIRMED','REVERSED')),
    version bigint NOT NULL DEFAULT 1,
    created_by uuid NOT NULL REFERENCES user_account(id),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    confirmed_at timestamptz
);
CREATE UNIQUE INDEX confirmed_movement_fact_idx ON treasury_movement(account_id,fact_key) WHERE confirmed_at IS NOT NULL;
CREATE INDEX treasury_project_idx ON treasury_movement(project_id,created_at);
CREATE TABLE treasury_effect (
    id uuid PRIMARY KEY,
    movement_id uuid NOT NULL REFERENCES treasury_movement(id),
    amount numeric(20,2) NOT NULL CHECK (amount<>0),
    effective_on date NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    original_id uuid REFERENCES treasury_effect(id),
    actor_id uuid NOT NULL REFERENCES user_account(id),
    reason varchar(1000) NOT NULL
);
CREATE TABLE collection_application (
    id uuid PRIMARY KEY,
    movement_id uuid NOT NULL REFERENCES treasury_movement(id),
    project_id uuid NOT NULL REFERENCES project(id),
    amount numeric(20,2) NOT NULL CHECK (amount<>0),
    original_id uuid REFERENCES collection_application(id),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    actor_id uuid NOT NULL REFERENCES user_account(id),
    reason varchar(1000) NOT NULL
);
CREATE TABLE payment_application (
    id uuid PRIMARY KEY,
    movement_id uuid NOT NULL REFERENCES treasury_movement(id),
    expense_id uuid NOT NULL REFERENCES expense(id),
    project_id uuid NOT NULL REFERENCES project(id),
    amount numeric(23,5) NOT NULL CHECK (amount<>0),
    application_kind varchar(15) NOT NULL CHECK (application_kind IN ('PAYMENT','ADVANCE')),
    original_id uuid REFERENCES payment_application(id),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    actor_id uuid NOT NULL REFERENCES user_account(id),
    reason varchar(1000) NOT NULL
);
CREATE INDEX payment_application_expense_idx ON payment_application(expense_id,project_id,recorded_at);
CREATE TABLE deposit_report (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES project(id),
    money_amount numeric(20,2) NOT NULL CHECK (money_amount>0),
    currency char(3) NOT NULL CHECK (currency='CRC'),
    operation_on date NOT NULL,
    payment_method varchar(30) NOT NULL CHECK (payment_method IN ('BANK_TRANSFER','CASH','CARD','OTHER')),
    bank_reference varchar(160) NOT NULL DEFAULT '',
    evidence_id uuid NOT NULL REFERENCES document(id),
    note varchar(1000) NOT NULL DEFAULT '',
    state varchar(20) NOT NULL DEFAULT 'REPORTED' CHECK(state IN ('REPORTED','IN_REVIEW','CONFIRMED','REJECTED','DUPLICATE','REVERSED')),
    movement_id uuid REFERENCES treasury_movement(id),
    version bigint NOT NULL DEFAULT 1,
    created_by uuid NOT NULL REFERENCES user_account(id),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    decision_reason varchar(1000)
);
CREATE INDEX deposit_project_idx ON deposit_report(project_id,created_at);

CREATE TABLE financial_state_event (
    sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_id uuid NOT NULL,
    aggregate_type varchar(10) NOT NULL CHECK(aggregate_type IN ('EXPENSE','DEPOSIT')),
    state varchar(20) NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX financial_state_cut_idx ON financial_state_event(aggregate_id,recorded_at,sequence);
CREATE FUNCTION record_m2_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' OR NEW.state<>OLD.state THEN
        INSERT INTO financial_state_event(aggregate_id,aggregate_type,state) VALUES(NEW.id,CASE WHEN TG_TABLE_NAME='expense' THEN 'EXPENSE' ELSE 'DEPOSIT' END,NEW.state);
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER expense_state_history AFTER INSERT OR UPDATE ON expense FOR EACH ROW EXECUTE FUNCTION record_m2_state();
CREATE TRIGGER deposit_state_history AFTER INSERT OR UPDATE ON deposit_report FOR EACH ROW EXECUTE FUNCTION record_m2_state();

-- Hechos aprobados y sus fuentes no se reescriben; cambios requieren eventos inversos.
CREATE FUNCTION protect_m2_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'M2 history is immutable'; END;
$$;
DO $$ DECLARE tab text; BEGIN
    FOREACH tab IN ARRAY ARRAY['cost_recognition','treasury_effect','collection_application','payment_application','fiscal_voucher','fiscal_voucher_document','expense_allocation','expense_revision','financial_state_event'] LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE OR TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION protect_m2_history()',tab||'_immutable',tab);
    END LOOP;
END $$;
CREATE FUNCTION protect_m2_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR TG_OP='TRUNCATE' THEN RAISE EXCEPTION 'M2 source cannot be deleted'; END IF;
    IF TG_TABLE_NAME='expense' THEN
        IF OLD.state IN ('VALIDATED','REVERSED') AND (to_jsonb(NEW)-ARRAY['state','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['state','version']) THEN RAISE EXCEPTION 'Validated expense is immutable'; END IF;
    ELSIF TG_TABLE_NAME='treasury_movement' THEN
        IF OLD.confirmed_at IS NOT NULL AND (to_jsonb(NEW)-ARRAY['state','version']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['state','version']) THEN RAISE EXCEPTION 'Confirmed movement is immutable'; END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER expense_source_guard BEFORE UPDATE OR DELETE ON expense FOR EACH ROW EXECUTE FUNCTION protect_m2_source();
CREATE TRIGGER movement_source_guard BEFORE UPDATE OR DELETE ON treasury_movement FOR EACH ROW EXECUTE FUNCTION protect_m2_source();
CREATE TRIGGER expense_truncate_guard BEFORE TRUNCATE ON expense FOR EACH STATEMENT EXECUTE FUNCTION protect_m2_history();
CREATE TRIGGER movement_truncate_guard BEFORE TRUNCATE ON treasury_movement FOR EACH STATEMENT EXECUTE FUNCTION protect_m2_history();
