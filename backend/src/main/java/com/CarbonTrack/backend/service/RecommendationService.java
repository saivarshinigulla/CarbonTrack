package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.dto.RecommendationResponse;
import com.CarbonTrack.backend.repository.ActivityLogRepository;
import com.CarbonTrack.backend.repository.ActivityTypeEmissionProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private final ActivityLogRepository activityLogRepository;

    public RecommendationService(
            ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public List<RecommendationResponse> getWeeklyRecommendations(Long userId) {

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);

        return activityLogRepository.findTopEmittingActivityTypes(
                        userId, startDate, endDate)
                .stream()
                .limit(3)
                .map(activity -> new RecommendationResponse(
                        activity.getActivityType(),
                        activity.getTotalEmissions(),
                        getTips(activity.getActivityType())
                ))
                .toList();
    }

    private List<String> getTips(String activityType) {

        String type = activityType == null
                ? ""
                : activityType.toLowerCase(Locale.ROOT);

        if (type.contains("car") || type.contains("vehicle")
                || type.contains("driving") || type.contains("travel")) {
            return List.of(
                    "Replace short car journeys with walking or cycling.",
                    "Use public transport or carpool for regular journeys.",
                    "Combine multiple errands into one trip."
            );
        }

        if (type.contains("flight") || type.contains("air")) {
            return List.of(
                    "Choose train or bus travel when practical.",
                    "Prefer direct flights to reduce unnecessary fuel use.",
                    "Consider virtual meetings instead of avoidable flights."
            );
        }

        if (type.contains("electricity") || type.contains("energy")
                || type.contains("power")) {
            return List.of(
                    "Switch off lights and appliances when not in use.",
                    "Use energy-efficient appliances and LED lighting.",
                    "Reduce unnecessary air-conditioning usage."
            );
        }

        if (type.contains("food") || type.contains("meal")
                || type.contains("meat") || type.contains("diet")) {
            return List.of(
                    "Include more plant-based meals in your weekly diet.",
                    "Plan meals to avoid food waste.",
                    "Choose seasonal and locally produced food where practical."
            );
        }

        if (type.contains("shopping") || type.contains("purchase")
                || type.contains("product")) {
            return List.of(
                    "Choose durable products instead of frequent replacements.",
                    "Prefer reusable products and minimal packaging.",
                    "Buy only what you need and consider second-hand options."
            );
        }

        return List.of(
                "Reduce the frequency of this activity where practical.",
                "Look for a lower-carbon alternative.",
                "Track this activity weekly to measure improvements."
        );
    }
}