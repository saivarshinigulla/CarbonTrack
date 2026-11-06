
package com.CarbonTrack.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "target_reduction_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetReductionPct;

    @Column(name = "period_days", nullable = false)
    private Integer periodDays;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "baseline_start_date")
    private LocalDate baselineStartDate;

    @Column(name = "baseline_end_date")
    private LocalDate baselineEndDate;

    @Column(name = "baseline_period_days")
    private Integer baselinePeriodDays;

    @Column(name = "baseline_footprint_kg", precision = 12, scale = 4)
    private BigDecimal baselineFootprintKg;

    protected Goal() {
        // Required by JPA
    }

    public Goal(
            User user,
            BigDecimal targetReductionPct,
            Integer periodDays,
            LocalDate startDate,
            String status) {
        this.user = user;
        this.targetReductionPct = targetReductionPct;
        this.periodDays = periodDays;
        this.startDate = startDate;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getTargetReductionPct() {
        return targetReductionPct;
    }

    public Integer getPeriodDays() {
        return periodDays;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getBaselineStartDate() {
        return baselineStartDate;
    }

    public LocalDate getBaselineEndDate() {
        return baselineEndDate;
    }

    public Integer getBaselinePeriodDays() {
        return baselinePeriodDays;
    }

    public BigDecimal getBaselineFootprintKg() {
        return baselineFootprintKg;
    }

    public void setTargetReductionPct(BigDecimal targetReductionPct) {
        this.targetReductionPct = targetReductionPct;
    }

    public void setPeriodDays(Integer periodDays) {
        this.periodDays = periodDays;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setBaselineStartDate(LocalDate baselineStartDate) {
        this.baselineStartDate = baselineStartDate;
    }

    public void setBaselineEndDate(LocalDate baselineEndDate) {
        this.baselineEndDate = baselineEndDate;
    }

    public void setBaselinePeriodDays(Integer baselinePeriodDays) {
        this.baselinePeriodDays = baselinePeriodDays;
    }

    public void setBaselineFootprintKg(BigDecimal baselineFootprintKg) {
        this.baselineFootprintKg = baselineFootprintKg;
    }
}
