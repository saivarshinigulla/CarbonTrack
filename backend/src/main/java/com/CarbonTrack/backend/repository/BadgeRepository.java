package com.CarbonTrack.backend.repository;

import com.CarbonTrack.backend.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.util.Optional;

public interface BadgeRepository extends JpaRepository<Badge, Long> {
    Optional<Badge> findByTriggerTypeAndThreshold(String triggerType, BigDecimal threshold);
}
