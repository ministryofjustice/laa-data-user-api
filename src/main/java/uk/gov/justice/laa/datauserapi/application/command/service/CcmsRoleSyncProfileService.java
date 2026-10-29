package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CcmsRoleSyncProfileService {

    private final UserProfileCommandRepository repository;
    private final RoleChangeNotificationService notificationService;
    private final RetryTemplate retry = RetryTemplate.builder().maxAttempts(3)
            .fixedBackoff(100).retryOn(CcmsSyncException.class).build();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProfileSyncResult syncProfile(UUID id, String actorOid) {
        var profile = repository.findById(id).orElse(null);
        if (profile == null) {
            log.info("Skipping deleted CCMS profile",
                    kv("profileId", id),
                    kv("actorEntraOid", actorOid));
            return new ProfileSyncResult(false, null);
        }
        if (profile.getLegacyUserId() == null || (profile.getUserType() == UserType.EXTERNAL
                && (profile.getFirm() == null || profile.getFirm().getCode() == null
                || profile.getFirm().getCode().isBlank()))
                || profile.getAppRoles().stream().filter(AppRole::isLegacySync).anyMatch(role ->
                role.getApp().getEntraOid() == null || role.getApp().getEntraOid().isBlank()
                        || role.getRoleIdentifier() == null || role.getRoleIdentifier().isBlank())) {
            persistSyncResult(id, false, actorOid);
            log.error("CCMS profile is missing a required identifier",
                    kv("profileId", id),
                    kv("actorEntraOid", actorOid));
            return new ProfileSyncResult(true,
                    new CcmsSyncException(HttpStatus.BAD_GATEWAY, "A required CCMS identifier is missing."));
        }
        Map<String, Set<String>> rolesByApp = profile.getAppRoles().stream()
                .filter(AppRole::isLegacySync)
                .collect(Collectors.groupingBy(role -> role.getApp().getEntraOid(),
                        Collectors.mapping(AppRole::getRoleIdentifier, Collectors.toSet())));
        CcmsSyncException failure = rolesByApp.isEmpty()
                ? new CcmsSyncException(HttpStatus.BAD_GATEWAY, "No legacy-sync roles remain for a selected CCMS profile.")
                : null;
        for (var entry : rolesByApp.entrySet()) {
            try {
                retry.execute(context -> {
                    notificationService.sendSyncMessage(profile, entry.getKey(), entry.getValue());
                    return null;
                });
            } catch (CcmsSyncException ex) {
                log.warn("CCMS role synchronization failed",
                        kv("profileId", id),
                        kv("applicationEntraOid", entry.getKey()),
                        kv("actorEntraOid", actorOid),
                        ex);
                failure = preferFailure(failure, ex);
            }
        }
        persistSyncResult(id, failure == null, actorOid);
        // Return the downstream failure so this profile's flag commits before the batch reports it.
        return new ProfileSyncResult(true, failure);
    }

    public record ProfileSyncResult(boolean processed, CcmsSyncException failure) {
    }

    private void persistSyncResult(UUID id, boolean successful, String actorOid) {
        // Don't change the loaded profile so a flush cannot overwrite concurrent profile edits
        if (repository.updateCcmsSyncResult(id, successful, actorOid, LocalDateTime.now()) != 1) {
            throw new ObjectOptimisticLockingFailureException(UserProfile.class, id);
        }
    }

    static CcmsSyncException preferFailure(CcmsSyncException existing, CcmsSyncException next) {
        return existing == null || next.getStatus().value() > existing.getStatus().value() ? next : existing;
    }
}
