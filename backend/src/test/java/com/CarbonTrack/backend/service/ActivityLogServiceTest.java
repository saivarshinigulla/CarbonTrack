package com.CarbonTrack.backend.service;

import com.CarbonTrack.backend.dto.CreateActivityRequest;
import com.CarbonTrack.backend.entity.ActivityLog;
import com.CarbonTrack.backend.entity.User;
import com.CarbonTrack.backend.event.ActivityLoggedEvent;
import com.CarbonTrack.backend.repository.ActivityLogRepository;
import com.CarbonTrack.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivityLogServiceTest {

    private ActivityLogRepository activityRepository;
    private UserRepository userRepository;
    private EmissionCalculationService emissionService;
    private ApplicationEventPublisher eventPublisher;
    private ActivityLogService service;
    private User user;

    @BeforeEach
    void setUp() {
        activityRepository = mock(ActivityLogRepository.class);
        userRepository = mock(UserRepository.class);
        emissionService = mock(EmissionCalculationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(userRepository.findByUsername("carbon-user"))
                .thenReturn(Optional.of(user));
        service = new ActivityLogService(
                activityRepository,
                userRepository,
                emissionService,
                eventPublisher
        );
    }

    @Test
    void updateRecalculatesEmissionsAndOnlyMutatesOwnedActivity() {
        LocalDate date = LocalDate.now();
        ActivityLog activity = activity();
        CreateActivityRequest request = request(date, "train_travel", "12");
        when(activityRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(activity));
        when(emissionService.calculateEmissions(
                "train_travel", "passenger-km", new BigDecimal("12")))
                .thenReturn(new BigDecimal("0.6000"));
        when(activityRepository.save(activity)).thenReturn(activity);

        ActivityLog updated = service.updateActivity(
                "carbon-user", 15L, request);

        assertSame(activity, updated);
        assertEquals("train_travel", updated.getActivityType());
        assertEquals(new BigDecimal("12"), updated.getQuantity());
        assertEquals(new BigDecimal("0.6000"), updated.getCo2eKg());
        assertEquals(date, updated.getLogDate());
        verify(activityRepository).save(activity);
        verify(emissionService).calculateEmissions(
                "train_travel", "passenger-km", new BigDecimal("12"));
        verify(eventPublisher).publishEvent(
                new ActivityLoggedEvent(7L, date));
    }

    @Test
    void updateDoesNotExposeAnotherUsersActivity() {
        when(activityRepository.findByIdAndUserId(99L, 7L))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.updateActivity(
                        "carbon-user", 99L, request(
                                LocalDate.now(), "car_travel", "1"))
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(activityRepository, never()).save(any());
        verify(emissionService, never()).calculateEmissions(any(), any(), any());
    }

    @Test
    void deleteOnlyRemovesOwnedActivity() {
        ActivityLog activity = activity();
        when(activityRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(activity));

        service.deleteActivity("carbon-user", 15L);

        verify(activityRepository).delete(activity);
    }

    @Test
    void allActivityMutationsEvictFootprintAnalytics() throws Exception {
        for (String method : new String[] {
                "createActivity",
                "updateActivity",
                "deleteActivity"
        }) {
            var annotated = java.util.Arrays.stream(
                    ActivityLogService.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(method))
                    .findFirst()
                    .orElseThrow();
            CacheEvict cacheEvict = annotated.getAnnotation(CacheEvict.class);
            assertNotNull(cacheEvict);
            assertArrayEquals(
                    new String[] {"footprintAnalytics"},
                    cacheEvict.cacheNames());
            assertTrue(cacheEvict.allEntries());
        }
    }

    private ActivityLog activity() {
        return new ActivityLog(
                user,
                "transport",
                "car_travel",
                BigDecimal.ONE,
                "km",
                new BigDecimal("0.1000"),
                LocalDate.now().minusDays(1),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private CreateActivityRequest request(
            LocalDate date, String type, String quantity) {
        return new CreateActivityRequest(
                "transport",
                type,
                new BigDecimal(quantity),
                "passenger-km",
                date,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
