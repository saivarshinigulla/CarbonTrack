-- Walking and cycling: direct operational emissions only.
-- Lifecycle emissions are excluded.

INSERT INTO emission_factors (
    activity_type,
    unit,
    kg_co2e_per_unit,
    source,
    effective_date
)
VALUES
    (
        'walking',
        'km',
        0.000000,
        'CarbonTrack assumption - direct operational emissions only',
        DATE '2026-11-05'
    ),
    (
        'cycling',
        'km',
        0.000000,
        'CarbonTrack assumption - direct operational emissions only',
        DATE '2026-11-05'
    )
ON CONFLICT (activity_type, unit) DO NOTHING;