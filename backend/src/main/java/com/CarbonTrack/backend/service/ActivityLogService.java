
package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.dto.CreateActivityRequest;
import com.CarbonTrack.backend.entity.ActivityLog;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.repository.ActivityLogRepository;
import com.CarbonTrack.backend.repository.UserRepository;
import com.CarbonTrack.backend.event.ActivityLoggedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final EmissionCalculationService emissionCalculationService;
    private final ApplicationEventPublisher eventPublisher;

    public ActivityLogService(
            ActivityLogRepository activityLogRepository,
            UserRepository userRepository,
            EmissionCalculationService emissionCalculationService,
            ApplicationEventPublisher eventPublisher) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
        this.emissionCalculationService = emissionCalculationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @CacheEvict(cacheNames = "footprintAnalytics", allEntries = true)
    public ActivityLog createActivity(
            String username, CreateActivityRequest request) {

        ActivityValues values = calculateValues(request);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authenticated user not found"));

        ActivityLog activityLog = new ActivityLog(
                user,
                values.category(),
                values.activityType(),
                values.quantity(),
                values.unit(),
                values.emissionsKg(),
                values.logDate(),
                values.energySource(),
                values.mealType(),
                values.servings(),
                values.productCategory(),
                values.spendAmount(),
                values.spendCurrency(),
                values.flightDistanceKm()
        );

        ActivityLog saved = activityLogRepository.save(activityLog);
        eventPublisher.publishEvent(new ActivityLoggedEvent(user.getId(), saved.getLogDate()));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = "footprintAnalytics", allEntries = true)
    public ActivityLog updateActivity(
            String username, Long activityId, CreateActivityRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authenticated user not found"));
        ActivityLog activity = activityLogRepository
                .findByIdAndUserId(activityId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Activity was not found"));
        ActivityValues values = calculateValues(request);

        activity.updateDetails(
                values.category(),
                values.activityType(),
                values.quantity(),
                values.unit(),
                values.emissionsKg(),
                values.logDate(),
                values.energySource(),
                values.mealType(),
                values.servings(),
                values.productCategory(),
                values.spendAmount(),
                values.spendCurrency(),
                values.flightDistanceKm()
        );

        ActivityLog saved = activityLogRepository.save(activity);
        eventPublisher.publishEvent(
                new ActivityLoggedEvent(user.getId(), saved.getLogDate()));
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = "footprintAnalytics", allEntries = true)
    public void deleteActivity(String username, Long activityId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authenticated user not found"));
        ActivityLog activity = activityLogRepository
                .findByIdAndUserId(activityId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Activity was not found"));
        activityLogRepository.delete(activity);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> getActivitiesForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authenticated user not found"));

        return activityLogRepository.findByUserIdOrderByLogDateDesc(user.getId());
    }

    private ActivityValues calculateValues(CreateActivityRequest request) {
        String category = request.category().toLowerCase(Locale.ROOT);
        String activityType = request.activityType().toLowerCase(Locale.ROOT);

        validateCategory(category, activityType);
        validateCategoryDetails(category, activityType, request);

        BigDecimal quantity = request.quantity();
        String unit = request.unit();
        if ("transport".equals(category)
                && "flight_travel".equals(activityType)) {
            if (request.flightDistanceKm() == null) {
                throw new IllegalArgumentException(
                        "Flight distance is required for flight travel");
            }
            quantity = request.flightDistanceKm();
            unit = "passenger-km";
        }

        BigDecimal emissionsKg = emissionCalculationService.calculateEmissions(
                activityType, unit, quantity);

        return new ActivityValues(
                category,
                activityType,
                quantity,
                unit,
                emissionsKg,
                request.logDate(),
                request.energySource(),
                request.mealType(),
                request.servings(),
                request.productCategory(),
                request.spendAmount(),
                request.spendCurrency() == null
                        ? null : request.spendCurrency().toUpperCase(Locale.ROOT),
                request.flightDistanceKm()
        );
    }

    private void validateCategoryDetails(
            String category, String activityType, CreateActivityRequest request) {

        switch (category) {
            case "transport" -> {
                if ("flight_travel".equals(activityType)) {
                    requirePresent(request.flightDistanceKm(), "Flight distance");
                    rejectPresent(request.energySource(), "Energy source");
                    rejectPresent(request.mealType(), "Meal type");
                    rejectPresent(request.servings(), "Servings");
                    rejectPresent(request.productCategory(), "Product category");
                    rejectPresent(request.spendAmount(), "Spend amount");
                    rejectPresent(request.spendCurrency(), "Spend currency");
                } else {
                    rejectPresent(request.flightDistanceKm(), "Flight distance");
                    rejectPresent(request.energySource(), "Energy source");
                    rejectPresent(request.mealType(), "Meal type");
                    rejectPresent(request.servings(), "Servings");
                    rejectPresent(request.productCategory(), "Product category");
                    rejectPresent(request.spendAmount(), "Spend amount");
                    rejectPresent(request.spendCurrency(), "Spend currency");
                }
            }
            case "electricity" -> {
                requireText(request.energySource(), "Energy source");
                rejectPresent(request.mealType(), "Meal type");
                rejectPresent(request.servings(), "Servings");
                rejectPresent(request.productCategory(), "Product category");
                rejectPresent(request.spendAmount(), "Spend amount");
                rejectPresent(request.spendCurrency(), "Spend currency");
                rejectPresent(request.flightDistanceKm(), "Flight distance");
            }
            case "food" -> {
                requireText(request.mealType(), "Meal type");
                requirePresent(request.servings(), "Servings");
                rejectPresent(request.energySource(), "Energy source");
                rejectPresent(request.productCategory(), "Product category");
                rejectPresent(request.spendAmount(), "Spend amount");
                rejectPresent(request.spendCurrency(), "Spend currency");
                rejectPresent(request.flightDistanceKm(), "Flight distance");
            }
            case "shopping" -> {
                requireText(request.productCategory(), "Product category");
                requirePresent(request.spendAmount(), "Spend amount");
                requireText(request.spendCurrency(), "Spend currency");
                rejectPresent(request.energySource(), "Energy source");
                rejectPresent(request.mealType(), "Meal type");
                rejectPresent(request.servings(), "Servings");
                rejectPresent(request.flightDistanceKm(), "Flight distance");
            }
            default -> throw new IllegalArgumentException("Unsupported category");
        }
    }

    private void validateCategory(String category, String activityType) {
        boolean valid = switch (category) {
            case "transport" ->
                    List.of(
                            "car_travel",
                            "motorcycle_travel",
                            "bus_travel",
                            "train_travel",
                            "flight_travel",
                            "walking",
                            "cycling"
                    ).contains(activityType);
            case "electricity" -> "grid_electricity".equals(activityType);
            case "food" ->
                    List.of("beef", "chicken", "plant_based_food").contains(activityType);
            case "shopping" ->
                    List.of("clothing", "electronics", "paper", "plastic")
                            .contains(activityType);
            default -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Activity type '" + activityType
                            + "' does not belong to category '" + category + "'");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void requirePresent(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void rejectPresent(Object value, String field) {
        if (value != null) {
            throw new IllegalArgumentException(
                    field + " is not applicable to this activity category");
        }
    }

    private record ActivityValues(
            String category,
            String activityType,
            BigDecimal quantity,
            String unit,
            BigDecimal emissionsKg,
            java.time.LocalDate logDate,
            String energySource,
            String mealType,
            BigDecimal servings,
            String productCategory,
            BigDecimal spendAmount,
            String spendCurrency,
            BigDecimal flightDistanceKm) {
    }
}
