package com.expensetracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(name = "budget_alert_states", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "year", "month"})
})
public class BudgetAlertState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "year", nullable = false)
    private Short year;

    @Column(name = "month", nullable = false)
    private Short month;

    @Column(name = "threshold_50_fired", nullable = false)
    private Boolean threshold50Fired = false;

    @Column(name = "threshold_80_fired", nullable = false)
    private Boolean threshold80Fired = false;

    @Column(name = "threshold_100_fired", nullable = false)
    private Boolean threshold100Fired = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public BudgetAlertState() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Short getYear() {
        return year;
    }

    public void setYear(Short year) {
        this.year = year;
    }

    public Short getMonth() {
        return month;
    }

    public void setMonth(Short month) {
        this.month = month;
    }

    public Boolean getThreshold50Fired() {
        return threshold50Fired;
    }

    public void setThreshold50Fired(Boolean threshold50Fired) {
        this.threshold50Fired = threshold50Fired;
    }

    public Boolean getThreshold80Fired() {
        return threshold80Fired;
    }

    public void setThreshold80Fired(Boolean threshold80Fired) {
        this.threshold80Fired = threshold80Fired;
    }

    public Boolean getThreshold100Fired() {
        return threshold100Fired;
    }

    public void setThreshold100Fired(Boolean threshold100Fired) {
        this.threshold100Fired = threshold100Fired;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
