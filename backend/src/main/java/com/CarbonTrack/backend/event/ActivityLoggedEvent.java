package com.CarbonTrack.backend.event;

import java.time.LocalDate;

public record ActivityLoggedEvent(Long userId, LocalDate logDate) {
}
