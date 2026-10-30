package com.CarbonTrack.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationResponse(
        String activityType,
        BigDecimal totalEmissionsKg,
        List<String> recommendations
) {}