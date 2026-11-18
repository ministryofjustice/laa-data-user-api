package uk.gov.justice.laa.datauserapi.application.command.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.RoleOutboxDelivery;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.RoleChangeOutboxRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.ProfileRoleChangeRepository;
import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@Slf4j
public class RoleChangeOutboxState {
    private final RoleChangeOutboxRepository repository;
    private final ProfileRoleChangeRepository profiles;
    private final Clock clock;

    public RoleChangeOutboxState(RoleChangeOutboxRepository repository, ProfileRoleChangeRepository profiles,
                                 Clock profileTrialClock) {
        this.repository = repository;
        this.profiles = profiles;
        this.clock = profileTrialClock;
    }

    @Transactional
    public List<RoleOutboxDelivery> claim() {
        var now = clock.instant();
        return repository.findClaimable(now).stream().map(event -> {
            UUID owner = UUID.randomUUID();
            event.setLeaseOwner(owner);
            event.setLeaseUntil(now.plus(Duration.ofMinutes(5)));
            event.setAttempts(event.getAttempts() + 1);
            return new RoleOutboxDelivery(event.getId(), event.getEventId(), owner, event.getProfileId(),
                    event.getApplicationOid(), event.getGroupId(), event.getUserType(), event.getPayload());
        }).toList();
    }

    @Transactional
    public void complete(RoleOutboxDelivery delivery, boolean success) {
        final var lockedProfiles = profiles.findProfilesForUpdate(java.util.Set.of(delivery.profileId()));
        var stored = repository.findForUpdate(delivery.id());
        if (stored.isEmpty() || !delivery.leaseOwner().equals(stored.get().getLeaseOwner())
                || stored.get().getDeliveredAt() != null) {
            log.warn("Role outbox lease is no longer owned", kv("event", "role_outbox_lease_lost"), kv("eventId", delivery.eventId()));
            return;
        }
        var event = stored.get();
        if (success) {
            event.setDeliveredAt(clock.instant());
        } else {
            event.setNextAttemptAt(clock.instant().plusSeconds(Math.min(900L, 30L * Math.min(30, event.getAttempts()))));
        }
        event.setLeaseOwner(null);
        event.setLeaseUntil(null);
        repository.flush();
        if (success && !repository.existsByProfileIdAndDeliveredAtIsNull(delivery.profileId())) {
            lockedProfiles.forEach(profile -> profile.setLastCcmsSyncSuccessful(true));
        }
    }
}
