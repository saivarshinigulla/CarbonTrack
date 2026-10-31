INSERT INTO badges (name, description, trigger_type, threshold) VALUES
('7-Day Streak', 'Logged activities on 7 consecutive days', 'STREAK', 7),
('Goal Getter', 'Achieved your first reduction goal', 'GOAL', 1),
('Carbon Cutter 10', 'Reduced your footprint by 10 kg CO2e', 'REDUCTION', 10),
('Carbon Cutter 25', 'Reduced your footprint by 25 kg CO2e', 'REDUCTION', 25),
('Carbon Cutter 50', 'Reduced your footprint by 50 kg CO2e', 'REDUCTION', 50)
ON CONFLICT (name) DO NOTHING;
