
package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.dto.GoalProgressResponse;
import com.CarbonTrack.backend.dto.GoalRequest;
import com.CarbonTrack.backend.dto.GoalResponse;
import com.CarbonTrack.backend.entity.ActivityLog;
import com.CarbonTrack.backend.entity.Goal;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.event.GoalAchievedEvent;
import com.CarbonTrack.backend.repository.ActivityLogRepository;
import com.CarbonTrack.backend.repository.GoalRepository;
import com.CarbonTrack.backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class GoalService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final int BASELINE_DAYS = 7;

    private final ApplicationEventPublisher eventPublisher;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;

    public GoalService(
            GoalRepository goalRepository,
            UserRepository userRepository,
            ActivityLogRepository activityLogRepository,
            ApplicationEventPublisher eventPublisher) {
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
        this.activityLogRepository = activityLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public GoalResponse createGoal(String username, GoalRequest request) {
        User user = requireUser(username);
        validateRequest(request);

        Goal goal = new Goal(
                user,
                request.targetReductionPct(),
                request.periodDays(),
                request.startDate(),
                "ACTIVE"
        );
        initializeBaseline(
                goal, user.getId(), request.startDate(), LocalDate.now());

        return toResponse(goalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> getGoals(String username) {
        User user = requireUser(username);

        return goalRepository.findAllByUserIdOrderByStartDateDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(String username, Long goalId) {
        User user = requireUser(username);
        return toResponse(requireOwnedGoal(goalId, user.getId()));
    }

    @Transactional
    public GoalResponse updateGoal(
            String username, Long goalId, GoalRequest request) {
        User user = requireUser(username);
        validateRequest(request);

        Goal goal = requireOwnedGoal(goalId, user.getId());

        if (!"ACTIVE".equalsIgnoreCase(goal.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only active goals can be updated"
            );
        }

        goal.setTargetReductionPct(request.targetReductionPct());
        goal.setPeriodDays(request.periodDays());
        initializeBaseline(
                goal, user.getId(), request.startDate(), LocalDate.now());

        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void deleteGoal(String username, Long goalId) {
        User user = requireUser(username);
        Goal goal = requireOwnedGoal(goalId, user.getId());
        goalRepository.delete(goal);
    }

    @Transactional
    public GoalProgressResponse getProgress(String username, Long goalId) {
        User user = requireUser(username);
        Goal goal = requireOwnedGoal(goalId, user.getId());

        LocalDate today = LocalDate.now();
        if ("BASELINE_PENDING".equalsIgnoreCase(goal.getStatus())) {
            if (goal.getBaselineStartDate() == null
                    || goal.getBaselineEndDate() == null) {
                initializeBaseline(
                        goal, user.getId(), goal.getStartDate(), today);
            }

            if (!today.isAfter(goal.getBaselineEndDate())) {
                return pendingBaselineProgress(goal);
            }

            BigDecimal collectedBaseline = totalEmissions(
                    user.getId(),
                    goal.getBaselineStartDate(),
                    goal.getBaselineEndDate());
            if (collectedBaseline.compareTo(BigDecimal.ZERO) > 0) {
                goal.setBaselineFootprintKg(collectedBaseline);
                goal.setBaselinePeriodDays(BASELINE_DAYS);
                goal.setStatus("ACTIVE");
                goal.setStartDate(goal.getBaselineEndDate().plusDays(1));
                goalRepository.save(goal);
            } else {
                goal.setBaselineStartDate(today);
                goal.setBaselineEndDate(today.plusDays(BASELINE_DAYS - 1L));
                goal.setBaselinePeriodDays(BASELINE_DAYS);
                goal.setStartDate(today.plusDays(BASELINE_DAYS));
                goalRepository.save(goal);
                return pendingBaselineProgress(goal);
            }
        }

        LocalDate start = goal.getStartDate();
        LocalDate end = start.plusDays(goal.getPeriodDays() - 1L);

        LocalDate baselineStart = goal.getBaselineStartDate();
        LocalDate baselineEnd = goal.getBaselineEndDate();
        BigDecimal baseline = goal.getBaselineFootprintKg();
        Integer baselinePeriodDays = goal.getBaselinePeriodDays();
        if (baseline == null) {
            baselineStart = start.minusDays(goal.getPeriodDays());
            baselineEnd = start.minusDays(1);
            baseline = totalEmissions(user.getId(), baselineStart, baselineEnd);
            baselinePeriodDays = goal.getPeriodDays();
            if (baseline.compareTo(BigDecimal.ZERO) > 0) {
                goal.setBaselineStartDate(baselineStart);
                goal.setBaselineEndDate(baselineEnd);
                goal.setBaselinePeriodDays(baselinePeriodDays);
                goal.setBaselineFootprintKg(baseline);
                goalRepository.save(goal);
            }
        }
        if (baselinePeriodDays == null || baselinePeriodDays < 1) {
            baselinePeriodDays = goal.getPeriodDays();
        }
        boolean baselineAvailable = baseline != null
                && baseline.compareTo(BigDecimal.ZERO) > 0;

        LocalDate observedEnd = today.isBefore(start)
                ? start.minusDays(1)
                : (today.isAfter(end) ? end : today);

        List<ActivityLog> currentLogs = observedEnd.isBefore(start)
                ? List.of()
                : activityLogRepository
                        .findByUserIdAndLogDateBetweenOrderByLogDateDesc(
                                user.getId(), start, observedEnd);
        BigDecimal currentTotal = currentLogs.stream()
                .map(ActivityLog::getCo2eKg)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        boolean currentDataAvailable = !currentLogs.isEmpty();
        BigDecimal target = baselineAvailable
                ? baseline
                        .multiply(BigDecimal.valueOf(goal.getPeriodDays()))
                        .divide(BigDecimal.valueOf(baselinePeriodDays),
                                4, RoundingMode.HALF_UP)
                        .multiply(ONE_HUNDRED.subtract(
                                goal.getTargetReductionPct()))
                        .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP)
                : null;

        int elapsedDays = today.isBefore(start)
                ? 0
                : (int) Math.min(
                goal.getPeriodDays(),
                ChronoUnit.DAYS.between(start, observedEnd) + 1
        );

        int daysRemaining = today.isBefore(start)
                ? goal.getPeriodDays()
                : (int) Math.max(
                0, ChronoUnit.DAYS.between(observedEnd, end)
        );

        BigDecimal projected = null;
        BigDecimal currentReduction = null;
        BigDecimal goalCompletion = null;
        Boolean onTrack = null;

        List<String> alerts = new ArrayList<>();

        if (today.isBefore(start)) {
            alerts.add("NOT_STARTED: This goal has not started yet.");

            if (!baselineAvailable) {
                alerts.add(
                        "BASELINE_UNAVAILABLE: No emissions were recorded during the baseline period; "
                                + "progress will be measurable once a valid baseline exists."
                );
            }
        } else if (baselineAvailable && currentDataAvailable && elapsedDays > 0) {
            BigDecimal baselineToDate = baseline
                    .multiply(BigDecimal.valueOf(elapsedDays))
                    .divide(BigDecimal.valueOf(baselinePeriodDays),
                            8, RoundingMode.HALF_UP);

            currentReduction = baselineToDate.subtract(currentTotal)
                    .multiply(ONE_HUNDRED)
                    .divide(
                            baselineToDate,
                            2,
                            RoundingMode.HALF_UP
                    );
            goalCompletion = currentReduction
                    .multiply(ONE_HUNDRED)
                    .divide(goal.getTargetReductionPct(), 2, RoundingMode.HALF_UP);

            projected = currentTotal
                    .multiply(BigDecimal.valueOf(goal.getPeriodDays()))
                    .divide(
                            BigDecimal.valueOf(elapsedDays),
                            4,
                            RoundingMode.HALF_UP
                    );

            onTrack = projected.compareTo(target) <= 0;

            if (onTrack) {
                alerts.add(
                        "ON_TRACK: Your recent footprint trend is within your goal budget."
                );
            } else {
                alerts.add(
                        "ACTION_NEEDED: Your recent footprint trend is above the target trajectory."
                );
            }
        } else if (!baselineAvailable) {
            alerts.add(
                    "BASELINE_UNAVAILABLE: No emissions were recorded during the baseline period; "
                            + "log activities in that period to calculate a meaningful reduction."
            );
        } else if (!currentDataAvailable && elapsedDays > 0) {
            alerts.add(
                    "CURRENT_DATA_UNAVAILABLE: Log activity data for this goal period before calculating reduction progress."
            );
        }

        String status = goal.getStatus();

        if (today.isAfter(end) && "ACTIVE".equalsIgnoreCase(status)) {
            if (baselineAvailable && currentDataAvailable
                    && currentTotal.compareTo(target) <= 0) {
                status = "ACHIEVED";
                alerts.add("GOAL_ACHIEVED: You met your footprint-reduction target.");
            } else {
                status = "MISSED";
                alerts.add("GOAL_MISSED: The goal period ended without meeting the target.");
            }

            goal.setStatus(status);
            goalRepository.save(goal);

            if ("ACHIEVED".equals(status)) {
                eventPublisher.publishEvent(
                        new GoalAchievedEvent(user.getId())
                );
            }
        } else if ("ACHIEVED".equalsIgnoreCase(status)) {
            alerts.add("GOAL_ACHIEVED: This goal was completed successfully.");
        } else if ("MISSED".equalsIgnoreCase(status)) {
            alerts.add("GOAL_MISSED: This goal ended without meeting its target.");
        }

        return new GoalProgressResponse(
                goal.getId(),
                status,
                goal.getTargetReductionPct(),
                goal.getPeriodDays(),
                start,
                end,
                baselineAvailable ? scale(baseline) : null,
                target == null ? null : scale(target),
                currentDataAvailable ? scale(currentTotal) : null,
                projected == null ? null : scale(projected),
                currentReduction,
                goalCompletion,
                elapsedDays,
                daysRemaining,
                onTrack,
                List.copyOf(alerts),
                baselinePeriodDays,
                baselineStart,
                baselineEnd
        );
    }

    private GoalProgressResponse pendingBaselineProgress(Goal goal) {
        List<String> alerts = List.of(
                "BASELINE_PENDING: Log activities during the seven-day baseline period before this goal can start.",
                "BASELINE_WINDOW: "
                        + goal.getBaselineStartDate()
                        + " through "
                        + goal.getBaselineEndDate()
        );
        return new GoalProgressResponse(
                goal.getId(),
                goal.getStatus(),
                goal.getTargetReductionPct(),
                goal.getPeriodDays(),
                goal.getStartDate(),
                goal.getStartDate().plusDays(goal.getPeriodDays() - 1L),
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                goal.getPeriodDays(),
                null,
                alerts,
                BASELINE_DAYS,
                goal.getBaselineStartDate(),
                goal.getBaselineEndDate()
        );
    }

    private void initializeBaseline(
            Goal goal,
            Long userId,
            LocalDate requestedStart,
            LocalDate today) {
        LocalDate historicalStart =
                requestedStart.minusDays(goal.getPeriodDays());
        LocalDate historicalEnd = requestedStart.minusDays(1);
        BigDecimal historicalBaseline = totalEmissions(
                userId, historicalStart, historicalEnd);

        if (historicalBaseline.compareTo(BigDecimal.ZERO) > 0) {
            goal.setStartDate(requestedStart);
            goal.setStatus("ACTIVE");
            goal.setBaselineStartDate(historicalStart);
            goal.setBaselineEndDate(historicalEnd);
            goal.setBaselinePeriodDays(goal.getPeriodDays());
            goal.setBaselineFootprintKg(historicalBaseline);
            return;
        }

        LocalDate earliestStart = today.plusDays(BASELINE_DAYS);
        LocalDate goalStart = requestedStart.isAfter(earliestStart)
                ? requestedStart : earliestStart;
        LocalDate baselineStart = goalStart.minusDays(BASELINE_DAYS);

        goal.setStartDate(goalStart);
        goal.setStatus("BASELINE_PENDING");
        goal.setBaselineStartDate(baselineStart);
        goal.setBaselineEndDate(goalStart.minusDays(1));
        goal.setBaselinePeriodDays(BASELINE_DAYS);
        goal.setBaselineFootprintKg(null);
    }

    private BigDecimal totalEmissions(
            Long userId, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            return ZERO;
        }

        return activityLogRepository
                .findByUserIdAndLogDateBetweenOrderByLogDateDesc(
                        userId, start, end
                )
                .stream()
                .map(ActivityLog::getCo2eKg)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found"
                ));
    }

    private Goal requireOwnedGoal(Long goalId, Long userId) {
        if (goalId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Goal was not found"
            );
        }

        return goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Goal was not found"
                ));
    }

    private void validateRequest(GoalRequest request) {
        if (request == null
                || request.targetReductionPct() == null
                || request.periodDays() == null
                || request.startDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetReductionPct, periodDays and startDate are required"
            );
        }

        if (request.targetReductionPct().compareTo(new BigDecimal("0.01")) < 0
                || request.targetReductionPct().compareTo(ONE_HUNDRED) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "targetReductionPct must be greater than 0 and no greater than 100"
            );
        }

        if (request.periodDays() < 7 || request.periodDays() > 365) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "periodDays must be between 7 and 365"
            );
        }
    }

    private GoalResponse toResponse(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getTargetReductionPct(),
                goal.getPeriodDays(),
                goal.getStartDate(),
                goal.getStartDate().plusDays(goal.getPeriodDays() - 1L),
                goal.getStatus()
        );
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
