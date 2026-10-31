
-- Update existing reduction badges to percentage-based milestones.

UPDATE badges
SET name = 'Eco Explorer',
    description = 'Reduced your carbon footprint by 10 percent'
WHERE trigger_type = 'REDUCTION'
  AND threshold = 10;

UPDATE badges
SET name = 'Carbon Cutter',
    description = 'Reduced your carbon footprint by 20 percent',
    threshold = 20
WHERE trigger_type = 'REDUCTION'
  AND threshold = 25;

UPDATE badges
SET name = 'Climate Champion',
    description = 'Reduced your carbon footprint by 50 percent'
WHERE trigger_type = 'REDUCTION'
  AND threshold = 50;

INSERT INTO badges (name, description, trigger_type, threshold)
VALUES
(
    'Green Starter',
    'Reduced your carbon footprint by 5 percent',
    'REDUCTION',
    5
),
(
    'Planet Protector',
    'Reduced your carbon footprint by 30 percent',
    'REDUCTION',
    30

);
