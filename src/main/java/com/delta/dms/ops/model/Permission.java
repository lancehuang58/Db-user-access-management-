package com.delta.dms.ops.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Permission entity representing time-based access permissions
 */
@Entity
@Table(name = "permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String resourceName;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PermissionType type;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PermissionStatus status = PermissionStatus.PENDING;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column(length = 100)
    private String revokedBy;

    @Column
    private LocalDateTime revokedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum PermissionType {
        READ,
        WRITE,
        DELETE,
        EXECUTE,
        ADMIN
    }

    public enum PermissionStatus {
        PENDING,
        APPROVED,
        ACTIVE,
        EXPIRED,
        REVOKED
    }

    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return status == PermissionStatus.ACTIVE &&
               now.isAfter(startTime) &&
               now.isBefore(endTime);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endTime);
    }
}
