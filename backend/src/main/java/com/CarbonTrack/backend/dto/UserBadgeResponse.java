package com.CarbonTrack.backend.dto;

import com.CarbonTrack.backend.entity.UserBadge;
import java.time.LocalDateTime;

public record UserBadgeResponse(
        Long badgeId,
        String name,
        String description,
        LocalDateTime awardedAt
) {
    public static UserBadgeResponse from(UserBadge userBadge) {
        return new UserBadgeResponse(
                userBadge.getBadge().getId(),
                userBadge.getBadge().getName(),
                userBadge.getBadge().getDescription(),
                userBadge.getAwardedAt()
        );
    }
}
