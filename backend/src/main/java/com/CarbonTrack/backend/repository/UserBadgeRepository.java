package com.CarbonTrack.backend.repository;

import com.CarbonTrack.backend.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserId(Long userId);
    boolean existsByUserIdAndBadgeId(Long userId, Long badgeId);

    @Modifying
    @Query(value = """
            insert into user_badges (user_id, badge_id, awarded_at)
            values (:userId, :badgeId, current_timestamp)
            on conflict (user_id, badge_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("badgeId") Long badgeId);
}
