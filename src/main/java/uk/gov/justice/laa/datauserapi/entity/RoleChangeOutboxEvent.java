package uk.gov.justice.laa.datauserapi.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role_change_outbox")
@Getter
@Setter
@NoArgsConstructor
public class RoleChangeOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;
    @Column(name = "profile_id", nullable = false)
    private UUID profileId;
    @Column(name = "application_oid", nullable = false)
    private String applicationOid;
    @Column(name = "group_id", nullable = false)
    private String groupId;
    @Column(name = "user_type", nullable = false)
    private String userType;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "lease_owner")
    private UUID leaseOwner;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
