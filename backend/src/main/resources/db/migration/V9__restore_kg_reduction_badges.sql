-- Restore required CO2e reduction milestones.

UPDATE badges
SET name = 'Eco Explorer',
    description = 'Reduced your carbon footprint by 10 kg CO2e',
    threshold = 10
WHERE trigger_type = 'REDUCTION' AND threshold = 10;

UPDATE badges
SET name = 'Carbon Cutter',
    description = 'Reduced your carbon footprint by 25 kg CO2e',
    threshold = 25
WHERE trigger_type = 'REDUCTION' AND threshold = 20;

UPDATE badges
SET name = 'Climate Champion',
    description = 'Reduced your carbon footprint by 50 kg CO2e',
    threshold = 50
WHERE trigger_type = 'REDUCTION' AND threshold = 50;

DELETE FROM badges
WHERE trigger_type = 'REDUCTION'
AND threshold IN (5, 30)
AND NOT EXISTS (
    SELECT 1 FROM user_badges ub
    WHERE ub.badge_id = badges.id
);
