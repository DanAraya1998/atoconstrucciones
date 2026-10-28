-- No reinterpretar referencias S3 de documentos existentes como IDs de Drive.
ALTER TABLE document ADD COLUMN storage_provider varchar(20) NOT NULL DEFAULT 'S3';
ALTER TABLE document ALTER COLUMN storage_provider SET DEFAULT 'GOOGLE_DRIVE';
ALTER TABLE document ADD CONSTRAINT document_storage_provider_check
    CHECK (storage_provider IN ('S3','GOOGLE_DRIVE'));

-- Reserva durable independiente del comando documental: sobrevive rollback/timeout.
-- Sin FK a document: la reserva debe persistir aunque el comando exterior falle.
CREATE TABLE drive_storage_object (
    object_key varchar(200) PRIMARY KEY,
    file_id varchar(100) NOT NULL UNIQUE,
    folder_id varchar(100) NOT NULL,
    filename varchar(220) NOT NULL,
    media_type varchar(100) NOT NULL,
    sha256 char(64) NOT NULL,
    expected_size bigint NOT NULL CHECK (expected_size > 0),
    revision_id varchar(100),
    created_at timestamptz NOT NULL DEFAULT now()
);
