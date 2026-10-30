package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.dto.CategoryBenchmarkResponse;
import com.CarbonTrack.backend.dto.PeerBenchmarkResponse;
import com.CarbonTrack.backend.repository.ActivityLogRepository;
import com.CarbonTrack.backend.repository.CategoryEmissionProjection;
import com.CarbonTrack.backend.repository.UserCategoryEmissionProjection;
import com.CarbonTrack.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PeerBenchmarkService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    public PeerBenchmarkService(
            ActivityLogRepository activityLogRepository,
            UserRepository userRepository) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
    }

    public PeerBenchmarkResponse getBenchmark(Long userId) {

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);

        long userCount = userRepository.count();

        if (userCount == 0) {
            return new PeerBenchmarkResponse(
                    startDate, endDate, 0, List.of());
        }

        List<CategoryEmissionProjection> platformTotals =
                activityLogRepository.findPlatformCategoryTotals(
                        startDate, endDate);

        List<UserCategoryEmissionProjection> userTotals =
                activityLogRepository.findUserCategoryTotals(
                        startDate, endDate);

        Map<String, BigDecimal> platformAverages = new HashMap<>();
        Map<String, BigDecimal> yourTotals = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> distributions = new HashMap<>();

        for (CategoryEmissionProjection row : platformTotals) {
            BigDecimal average = row.getTotalEmissions()
                    .divide(BigDecimal.valueOf(userCount), 2,
                            RoundingMode.HALF_UP);

            platformAverages.put(row.getCategory(), average);
        }

        for (UserCategoryEmissionProjection row : userTotals) {

            distributions.computeIfAbsent(
                    row.getCategory(), key -> new HashMap<>()
            ).put(row.getUserId(), row.getTotalEmissions());

            if (row.getUserId().equals(userId)) {
                yourTotals.put(row.getCategory(), row.getTotalEmissions());
            }
        }

        List<CategoryBenchmarkResponse> results = new ArrayList<>();

        for (String category : platformAverages.keySet()) {

            BigDecimal yourEmissions = yourTotals.getOrDefault(
                    category, BigDecimal.ZERO);

            Map<Long, BigDecimal> distribution =
                    distributions.getOrDefault(category, Map.of());

            long usersAtOrAbove = 0;

            for (BigDecimal emissions : distribution.values()) {
                if (emissions.compareTo(yourEmissions) >= 0) {
                    usersAtOrAbove++;
                }
            }

            // Users without activity have zero emissions.
            if (yourEmissions.compareTo(BigDecimal.ZERO) == 0) {
                usersAtOrAbove += userCount - distribution.size();
            }

            BigDecimal percentile = BigDecimal.valueOf(usersAtOrAbove)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(userCount), 2,
                            RoundingMode.HALF_UP);

            String standing;

            if (percentile.compareTo(BigDecimal.valueOf(75)) >= 0) {
                standing = "Excellent";
            } else if (percentile.compareTo(BigDecimal.valueOf(50)) >= 0) {
                standing = "Good";
            } else if (percentile.compareTo(BigDecimal.valueOf(25)) >= 0) {
                standing = "Average";
            } else {
                standing = "Needs Improvement";
            }

            results.add(new CategoryBenchmarkResponse(
                    category,
                    yourEmissions,
                    platformAverages.get(category),
                    percentile,
                    standing
            ));
        }

        return new PeerBenchmarkResponse(
                startDate,
                endDate,
                userCount,
                results
        );
    }
}