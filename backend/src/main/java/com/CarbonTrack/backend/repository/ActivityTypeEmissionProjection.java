package com.CarbonTrack.backend.repository;

import java.math.BigDecimal;

public interface ActivityTypeEmissionProjection {

    String getActivityType();

    BigDecimal getTotalEmissions();
}