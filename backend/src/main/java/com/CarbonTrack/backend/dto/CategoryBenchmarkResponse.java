package com.CarbonTrack.backend.dto;

import java.math.BigDecimal;

public record CategoryBenchmarkResponse(
        String category,
        BigDecimal yourEmissionsKg,
        BigDecimal platformAverageKg,
        BigDecimal percentile,
        String standing
) {}