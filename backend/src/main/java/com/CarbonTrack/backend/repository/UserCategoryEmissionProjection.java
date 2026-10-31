package com.CarbonTrack.backend.repository;

import java.math.BigDecimal;

public interface UserCategoryEmissionProjection {

    Long getUserId();

    String getCategory();

    BigDecimal getTotalEmissions();
}