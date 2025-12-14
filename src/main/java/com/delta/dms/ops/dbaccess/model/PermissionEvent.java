package com.delta.dms.ops.dbaccess.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * PermissionEvent entity for tracking permission-related events
 */
@Entity
@Table(name = "permission_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(length = 100)
    private String triggeredBy;

    @Column(length = 1000)
    private String eventDetails;

    @Column(nullable = false)
    private LocalDateTime eventTime;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum EventType {
        CREATED,
        APPROVED,
        ACTIVATED,
        EXPIRED,
        REVOKED,
        EXTENDED,
        MODIFIED
    }
}
