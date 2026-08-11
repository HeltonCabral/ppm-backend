ALTER TABLE committee_members
    ADD COLUMN IF NOT EXISTS member_code VARCHAR(100);

UPDATE committee_members
SET member_code = member_name
WHERE member_code IS NULL;

ALTER TABLE committee_members
    ALTER COLUMN member_code SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_committee_members_code_ci
    ON committee_members (committee_id, LOWER(member_code));