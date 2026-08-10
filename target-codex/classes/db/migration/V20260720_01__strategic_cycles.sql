-- PostgreSQL migration for strategic cycles. Apply after the existing schema.
ALTER TABLE strategic_plans RENAME COLUMN version TO revision;
ALTER TABLE strategic_plans ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE strategic_plans ADD COLUMN approval_date DATE;
ALTER TABLE strategic_plans ADD COLUMN approved_by VARCHAR(150);
ALTER TABLE strategic_plans ADD COLUMN created_by VARCHAR(150) NOT NULL DEFAULT 'system';
ALTER TABLE strategic_plans ADD COLUMN updated_by VARCHAR(150) NOT NULL DEFAULT 'system';
ALTER TABLE strategic_plans ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE strategic_plans ADD CONSTRAINT uk_cycle_name_period UNIQUE(name,start_year,end_year);
CREATE INDEX idx_cycle_status ON strategic_plans(status);
CREATE INDEX idx_cycle_period ON strategic_plans(start_year,end_year);

UPDATE strategic_plans SET status=CASE status WHEN 'ATIVO' THEN 'ACTIVE' WHEN 'REVISTO' THEN 'REPLACED' WHEN 'ARQUIVADO' THEN 'CLOSED' ELSE status END;

CREATE TABLE cycle_audit_log (
 id UUID PRIMARY KEY, cycle_id UUID NOT NULL REFERENCES strategic_plans(id), action VARCHAR(60) NOT NULL,
 from_status VARCHAR(30), to_status VARCHAR(30), comment TEXT, performed_by VARCHAR(150) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cycle_audit ON cycle_audit_log(cycle_id,created_at);

CREATE TABLE cycle_reviews (
 id UUID PRIMARY KEY, source_cycle_id UUID NOT NULL REFERENCES strategic_plans(id), status VARCHAR(20) NOT NULL,
 draft_json TEXT NOT NULL, created_cycle_id UUID REFERENCES strategic_plans(id), idempotency_key VARCHAR(150) UNIQUE,
 version BIGINT NOT NULL DEFAULT 0, created_by VARCHAR(150) NOT NULL, updated_by VARCHAR(150) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_review_source ON cycle_reviews(source_cycle_id);
CREATE INDEX idx_review_status ON cycle_reviews(status);
