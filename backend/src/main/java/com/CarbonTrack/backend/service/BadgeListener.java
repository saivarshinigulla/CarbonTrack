
package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.dto.GoalProgressResponse;
import com.CarbonTrack.backend.dto.GoalResponse;
import com.CarbonTrack.backend.entity.Badge;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.entity.UserBadge;
import com.CarbonTrack.backend.event.ActivityLoggedEvent;
import com.CarbonTrack.backend.event.GoalAchievedEvent;
import com.CarbonTrack.backend.repository.ActivityLogRepository;
import com.CarbonTrack.backend.repository.BadgeRepository;
import com.CarbonTrack.backend.repository.GoalRepository;
import com.CarbonTrack.backend.repository.UserBadgeRepository;
import com.CarbonTrack.backend.repository.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class BadgeListener {

    private final ActivityLogRepository activityLogRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final GoalService goalService;

    public BadgeListener(
            ActivityLogRepository activityLogRepository,
            BadgeRepository badgeRepository,
            UserBadgeRepository userBadgeRepository,
            UserRepository userRepository,
            GoalRepository goalRepository,
            GoalService goalService) {

        this.activityLogRepository = activityLogRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.userRepository = userRepository;
        this.goalRepository = goalRepository;
        this.goalService = goalService;
    }

    @EventListener
    @Transactional
    public void onGoalAchieved(GoalAchievedEvent event) {
        award(event.userId(), "GOAL", BigDecimal.ONE);
    }

    @EventListener
    @Transactional
    public void onActivityLogged(ActivityLoggedEvent event) {

        Long userId = event.userId();

        checkStreak(userId);
        checkReduction(userId, event.logDate());
        checkGoal(userId);
    }

    private void checkStreak(Long userId) {

        List<LocalDate> dates =
                activityLogRepository.findDistinctLogDatesByUserId(userId);

        if (dates.isEmpty()) return;

        Set<LocalDate> loggedDates = new HashSet<>(dates);

        LocalDate day = dates.get(0);
        int streak = 0;

        while (loggedDates.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }

        if (streak >= 7) {
            award(userId, "STREAK", BigDecimal.valueOf(7));
        }
    }

    private void checkReduction(Long userId, LocalDate activityDate) {

        User user = userRepository.findById(userId).orElse(null);

        if (user == null || activityDate == null) return;

        List<GoalResponse> goals =
                goalService.getGoals(user.getUsername());

        BigDecimal[] thresholds = {
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(50)
        };

        for (GoalResponse goal : goals) {

            if (!"ACTIVE".equalsIgnoreCase(goal.status())) {
                continue;
            }

            if (activityDate.isBefore(goal.startDate())
                    || activityDate.isAfter(goal.endDate())) {
                continue;
            }

            GoalProgressResponse progress =
                    goalService.getProgress(user.getUsername(), goal.id());

            BigDecimal baseline = progress.baselineFootprintKg();
            BigDecimal current = progress.currentFootprintKg();
            Integer elapsedDays = progress.elapsedDays();
            Integer periodDays = progress.periodDays();

            if (baseline == null || current == null
                    || elapsedDays == null || periodDays == null
                    || baseline.compareTo(BigDecimal.ZERO) <= 0
                    || elapsedDays <= 0 || periodDays <= 0) {
                continue;
            }

            // Compare equivalent elapsed periods.
            BigDecimal baselineToDate = baseline
                    .multiply(BigDecimal.valueOf(elapsedDays))
                    .divide(BigDecimal.valueOf(periodDays),
                            8, RoundingMode.HALF_UP);

            // Actual CO2e reduction in kilograms.
            BigDecimal reductionKg = baselineToDate.subtract(current);

            if (reductionKg.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            for (BigDecimal threshold : thresholds) {

                if (reductionKg.compareTo(threshold) >= 0) {
                    award(userId, "REDUCTION", threshold);
                }
            }
        }
    }

    private void checkGoal(Long userId) {

        boolean achieved = !goalRepository
                .findAllByUserIdAndStatus(userId, "ACHIEVED")
                .isEmpty();

        if (achieved) {
            award(userId, "GOAL", BigDecimal.ONE);
        }
    }

    private void award(Long userId, String type, BigDecimal threshold) {

        Badge badge = badgeRepository
                .findByTriggerTypeAndThreshold(type, threshold)
                .orElse(null);

        if (badge == null) return;

        if (userBadgeRepository.existsByUserIdAndBadgeId(
                userId, badge.getId())) {
            return;
        }

        userBadgeRepository.save(
                new UserBadge(
                        userRepository.getReferenceById(userId),
                        badge,
                        LocalDateTime.now()
                )
        );
    }
}
