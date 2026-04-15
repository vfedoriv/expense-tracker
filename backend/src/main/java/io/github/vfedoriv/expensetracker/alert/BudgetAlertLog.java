package io.github.vfedoriv.expensetracker.alert;

import io.github.vfedoriv.expensetracker.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "budget_alert_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetAlertLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer threshold;

    @Column(name = "alerted_at", nullable = false, updatable = false)
    private LocalDateTime alertedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    @PrePersist
    protected void onCreate() {
        alertedAt = LocalDateTime.now();
    }
}
