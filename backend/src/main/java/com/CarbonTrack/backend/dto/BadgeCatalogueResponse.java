package com.CarbonTrack.backend.dto;

import com.CarbonTrack.backend.entity.Badge;

import java.math.BigDecimal;

public record BadgeCatalogueResponse(
        Long id,
        String name,
        String description,
        String triggerType,
        BigDecimal threshold
) {
    public static BadgeCatalogueResponse from(Badge badge) {
        return new BadgeCatalogueResponse(
                badge.getId(),
                badge.getName(),
                badge.getDescription(),
                badge.getTriggerType(),
                badge.getThreshold()
        );
    }
}
