-- Program type and domain are managed through lookup values.
ALTER TABLE programs ADD COLUMN IF NOT EXISTS program_type_id UUID;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS domain_id UUID;

ALTER TABLE programs ADD CONSTRAINT fk_program_type FOREIGN KEY (program_type_id) REFERENCES lookup_values(id);
ALTER TABLE programs ADD CONSTRAINT fk_program_domain FOREIGN KEY (domain_id) REFERENCES lookup_values(id);

CREATE INDEX IF NOT EXISTS idx_program_type ON programs(program_type_id);
CREATE INDEX IF NOT EXISTS idx_program_domain ON programs(domain_id);

-- Existing rows must be assigned valid lookups before making these columns NOT NULL.
