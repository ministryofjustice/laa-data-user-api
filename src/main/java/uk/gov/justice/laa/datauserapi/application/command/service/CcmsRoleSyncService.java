package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

@Service
@Slf4j
@RequiredArgsConstructor
public class CcmsRoleSyncService {

    private final UserProfileCommandRepository repository;
    private final CcmsRoleSyncProfileService profileService;

    @Async("ccmsRoleSyncExecutor")
    public void sync(String actorOid) {
        log.info("Starting asynchronous CCMS role sync",
                kv("actorEntraOid", actorOid));
        var profileIds = repository.findUserProfileIdsForCcmsSync();
        int processed = 0;
        int successful = 0;
        int persistenceFailures = 0;
        CcmsSyncException failure = null;
        for (var profileId : profileIds) {
            CcmsRoleSyncProfileService.ProfileSyncResult result;
            try {
                result = profileService.syncProfile(profileId, actorOid);
            } catch (ConcurrencyFailureException | DataIntegrityViolationException ex) {
                processed++;
                persistenceFailures++;
                log.warn("CCMS profile synchronization transaction failed; continuing with remaining profiles",
                        kv("profileId", profileId),
                        kv("actorEntraOid", actorOid),
                        ex);
                continue;
            }
            if (!result.processed()) {
                continue;
            }
            processed++;
            if (result.failure() == null) {
                successful++;
            } else {
                failure = CcmsRoleSyncProfileService.preferFailure(failure, result.failure());
            }
        }
        if (failure != null || persistenceFailures > 0) {
            log.warn("CCMS role sync completed with failures; failed profiles remain eligible for retry",
                    kv("actorEntraOid", actorOid),
                    kv("processedProfiles", processed),
                    kv("successfulProfiles", successful),
                    kv("unsuccessfulProfiles", processed - successful),
                    kv("persistenceFailedProfiles", persistenceFailures),
                    failure);
        } else {
            log.info("CCMS role sync completed successfully",
                    kv("actorEntraOid", actorOid),
                    kv("processedProfiles", processed),
                    kv("successfulProfiles", successful),
                    kv("unsuccessfulProfiles", 0));
        }
    }
}
