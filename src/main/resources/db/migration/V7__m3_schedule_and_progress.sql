CREATE TABLE schedule (
    project_id uuid PRIMARY KEY REFERENCES project(id),
    revision bigint NOT NULL DEFAULT 1 CHECK(revision>0)
);
CREATE TABLE schedule_task (
    id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES schedule(project_id),
    parent_id uuid, kind varchar(12) NOT NULL CHECK(kind IN ('LEAF','SUMMARY','MILESTONE')),
    title varchar(160) NOT NULL, start_date date, end_exclusive date,
    state varchar(20) NOT NULL CHECK(state IN ('PLANNED','IN_PROGRESS','BLOCKED','COMPLETED','CANCELLED')),
    administrative boolean NOT NULL, client_visible boolean NOT NULL,
    extra_id uuid REFERENCES extra_request(id), verified_percent numeric(7,4) NOT NULL DEFAULT 0 CHECK(verified_percent BETWEEN 0 AND 100),
    verified_on date, verified_at timestamptz,
    UNIQUE(project_id,id), FOREIGN KEY(project_id,parent_id) REFERENCES schedule_task(project_id,id) DEFERRABLE INITIALLY DEFERRED,
    CHECK(kind='SUMMARY' OR (start_date IS NOT NULL AND end_exclusive IS NOT NULL AND
        ((kind='LEAF' AND start_date<end_exclusive) OR (kind='MILESTONE' AND start_date=end_exclusive))))
);
CREATE INDEX schedule_parent_idx ON schedule_task(project_id,parent_id);
CREATE TABLE task_assignment (
    task_id uuid NOT NULL REFERENCES schedule_task(id), supplier_id uuid NOT NULL REFERENCES party(id),
    PRIMARY KEY(task_id,supplier_id)
);
CREATE TABLE task_dependency (
    project_id uuid NOT NULL REFERENCES schedule(project_id), predecessor_id uuid NOT NULL, successor_id uuid NOT NULL,
    kind varchar(2) NOT NULL CHECK(kind='FS'), lag_days integer NOT NULL CHECK(lag_days BETWEEN 0 AND 3650),
    PRIMARY KEY(project_id,predecessor_id,successor_id), CHECK(predecessor_id<>successor_id),
    FOREIGN KEY(project_id,predecessor_id) REFERENCES schedule_task(project_id,id),
    FOREIGN KEY(project_id,successor_id) REFERENCES schedule_task(project_id,id)
);
CREATE INDEX schedule_successor_idx ON task_dependency(project_id,successor_id);
CREATE TABLE progress_report (
    id uuid PRIMARY KEY, project_id uuid NOT NULL, task_id uuid NOT NULL, reported_by uuid NOT NULL REFERENCES user_account(id),
    reported_percent numeric(7,4) NOT NULL CHECK(reported_percent BETWEEN 0 AND 100), observed_on date NOT NULL,
    evidence_id uuid NOT NULL REFERENCES document(id), note varchar(1000) NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY(project_id,task_id) REFERENCES schedule_task(project_id,id)
);
CREATE INDEX progress_report_project_idx ON progress_report(project_id,recorded_at,id);
CREATE TABLE progress_verification (
    report_id uuid PRIMARY KEY REFERENCES progress_report(id), verified_percent numeric(7,4) NOT NULL CHECK(verified_percent BETWEEN 0 AND 100),
    evidence_id uuid NOT NULL REFERENCES document(id), reason varchar(1000) NOT NULL,
    verified_by uuid NOT NULL REFERENCES user_account(id), recorded_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE progress_baseline (
    id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES schedule(project_id), scope_hash char(64) NOT NULL,
    scope_snapshot jsonb NOT NULL, reason varchar(1000) NOT NULL,
    created_by uuid NOT NULL REFERENCES user_account(id), created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE task_weight (
    baseline_id uuid NOT NULL REFERENCES progress_baseline(id), task_id uuid NOT NULL REFERENCES schedule_task(id),
    weight numeric(9,6) NOT NULL CHECK(weight BETWEEN 0 AND 100), exclusion_reason varchar(1000) NOT NULL,
    PRIMARY KEY(baseline_id,task_id)
);
CREATE TABLE progress_baseline_approval (
    baseline_id uuid PRIMARY KEY REFERENCES progress_baseline(id), project_id uuid NOT NULL REFERENCES schedule(project_id),
    schedule_revision bigint NOT NULL, approved_by uuid NOT NULL REFERENCES user_account(id),
    reason varchar(1000) NOT NULL, previous_percent varchar(30), percent varchar(30) NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX baseline_approved_idx ON progress_baseline_approval(project_id,recorded_at,schedule_revision);
CREATE TABLE schedule_revision (
    project_id uuid NOT NULL REFERENCES schedule(project_id), revision bigint NOT NULL,
    snapshot jsonb NOT NULL, client_snapshot jsonb NOT NULL, reason varchar(1000) NOT NULL,
    actor_id uuid NOT NULL REFERENCES user_account(id), recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(project_id,revision)
);
CREATE INDEX schedule_cut_idx ON schedule_revision(project_id,recorded_at,revision);
CREATE FUNCTION protect_m3_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'M3 history is immutable'; END;
$$;
DO $$ DECLARE tab text; BEGIN
    FOREACH tab IN ARRAY ARRAY['progress_report','progress_verification','progress_baseline','task_weight','progress_baseline_approval','schedule_revision'] LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE OR DELETE OR TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION protect_m3_history()',tab||'_immutable',tab);
    END LOOP;
END $$;
CREATE TRIGGER schedule_task_retained BEFORE DELETE ON schedule_task FOR EACH ROW EXECUTE FUNCTION protect_m3_history();
CREATE TRIGGER schedule_task_truncate_guard BEFORE TRUNCATE ON schedule_task FOR EACH STATEMENT EXECUTE FUNCTION protect_m3_history();
