CREATE FUNCTION protect_approved_notice() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF OLD.state='APPROVED' THEN RAISE EXCEPTION 'Approved notice is immutable'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER immutable_notice BEFORE UPDATE OR DELETE ON privacy_notice FOR EACH ROW EXECUTE FUNCTION protect_approved_notice();
CREATE FUNCTION protect_consent_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Consent history is retained'; END IF;
 IF OLD.withdrawn_at IS NOT NULL OR (NEW.project_id,NEW.document_id,NEW.notice_id,NEW.evidence_id,NEW.granted_by,NEW.purpose,NEW.channels,NEW.expires_on,NEW.granted_at)
 IS DISTINCT FROM (OLD.project_id,OLD.document_id,OLD.notice_id,OLD.evidence_id,OLD.granted_by,OLD.purpose,OLD.channels,OLD.expires_on,OLD.granted_at)
 THEN RAISE EXCEPTION 'Consent terms are immutable'; END IF; RETURN NEW;
END; $$;
CREATE TRIGGER immutable_consent_terms BEFORE UPDATE OR DELETE ON publication_consent FOR EACH ROW EXECUTE FUNCTION protect_consent_terms();
