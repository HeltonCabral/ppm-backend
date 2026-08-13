-- Add conversion flags to demands table
ALTER TABLE demands ADD COLUMN is_converted_to_project BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE demands ADD COLUMN is_converted_to_program BOOLEAN NOT NULL DEFAULT FALSE;

-- Create index for conversion flag queries
CREATE INDEX idx_demands_is_converted_to_project ON demands(is_converted_to_project);
CREATE INDEX idx_demands_is_converted_to_program ON demands(is_converted_to_program);
