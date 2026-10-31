package com.CarbonTrack.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record PeerBenchmarkResponse(
        LocalDate startDate,
        LocalDate endDate,
        long platformUserCount,
        List<CategoryBenchmarkResponse> categories
) {}