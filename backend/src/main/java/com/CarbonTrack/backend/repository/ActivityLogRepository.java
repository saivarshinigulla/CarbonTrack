
package com.CarbonTrack.backend.repository;

import com.CarbonTrack.backend.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUserIdOrderByLogDateDesc(Long userId);

    List<ActivityLog> findByUserIdAndLogDateBetweenOrderByLogDateDesc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
    @Query("""
        select a.category as category,
               sum(a.co2eKg) as totalEmissions
        from ActivityLog a
        where a.logDate between :startDate and :endDate
        group by a.category
        order by a.category
        """)
    List<CategoryEmissionProjection> findPlatformCategoryTotals(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        select a.user.id as userId,
               a.category as category,
               sum(a.co2eKg) as totalEmissions
        from ActivityLog a
        where a.logDate between :startDate and :endDate
        group by a.user.id, a.category
        """)
    List<UserCategoryEmissionProjection> findUserCategoryTotals(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
        select a.activityType as activityType,
               sum(a.co2eKg) as totalEmissions
        from ActivityLog a
        where a.user.id = :userId
          and a.logDate between :startDate and :endDate
        group by a.activityType
        order by sum(a.co2eKg) desc
        """)
    List<ActivityTypeEmissionProjection> findTopEmittingActivityTypes(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select a.category as category,
                   sum(a.co2eKg) as totalEmissions
            from ActivityLog a
            where a.user.id = :userId
              and a.logDate between :startDate and :endDate
            group by a.category
            order by a.category
            """)
    List<CategoryEmissionProjection> sumEmissionsByCategoryAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select a.logDate as logDate,
                   sum(a.co2eKg) as totalEmissions
            from ActivityLog a
            where a.user.id = :userId
              and a.logDate between :startDate and :endDate
            group by a.logDate
            order by a.logDate
            """)
    List<DateEmissionProjection> sumEmissionsByDateAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select distinct a.logDate
            from ActivityLog a
            where a.user.id = :userId
            order by a.logDate desc
            """)
    List<LocalDate> findDistinctLogDatesByUserId(
            @Param("userId") Long userId
    );

    @Query("""
            select sum(a.co2eKg)
            from ActivityLog a
            where a.user.id = :userId
              and a.logDate between :startDate and :endDate
            """)
    BigDecimal sumCo2eByUserIdAndLogDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
