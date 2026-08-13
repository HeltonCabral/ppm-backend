ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS reprioritization_reason VARCHAR(80);

ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS reprioritization_justification TEXT;

ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS reprioritized_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS reprioritized_by VARCHAR(150);
