package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsProfileSyncOutcome;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;

import java.util.EnumMap;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class CcmsRoleSyncService {

    private final UserProfileCommandRepository repository;
    private final CcmsRoleSyncProfileService profileService;

    @Async("ccmsRoleSyncExecutor")
    public void sync(String actorOid) {
        log.info("Starting asynchronous CCMS role sync",
                kv("actorEntraOid", actorOid));
        var profileIds = repository.findUserProfileIdsForCcmsSync();
        var counts = new EnumMap<CcmsProfileSyncOutcome, Integer>(CcmsProfileSyncOutcome.class);
        for (var profileId : profileIds) {
            counts.merge(syncProfile(profileId, actorOid), 1, Integer::sum);
        }
        int skipped = counts.getOrDefault(CcmsProfileSyncOutcome.SKIPPED, 0);
        int processed = profileIds.size() - skipped;
        int successful = counts.getOrDefault(CcmsProfileSyncOutcome.SUCCESS, 0);
        int unsuccessful = processed - successful;
        if (unsuccessful > 0) {
            log.warn("CCMS role sync completed with failures",
                    kv("actorEntraOid", actorOid),
                    kv("processedProfiles", processed),
                    kv("successfulProfiles", successful),
                    kv("unsuccessfulProfiles", unsuccessful),
                    kv("persistenceFailedProfiles", counts.getOrDefault(CcmsProfileSyncOutcome.PERSISTENCE_FAILURE, 0)),
                    kv("invalidProfiles", counts.getOrDefault(CcmsProfileSyncOutcome.INVALID_DATA, 0)),
                    kv("unexpectedFailedProfiles", counts.getOrDefault(CcmsProfileSyncOutcome.UNEXPECTED_FAILURE, 0)),
                    kv("skippedProfiles", skipped));
        } else {
            log.info("CCMS role sync completed successfully",
                    kv("actorEntraOid", actorOid),
                    kv("processedProfiles", processed),
                    kv("successfulProfiles", successful),
                    kv("unsuccessfulProfiles", 0),
                    kv("skippedProfiles", skipped));
        }
    }

    private CcmsProfileSyncOutcome syncProfile(UUID profileId, String actorOid) {
        try {
            return profileService.syncProfile(profileId, actorOid);
        } catch (ConcurrencyFailureException ex) {
            log.warn("CCMS profile synchronisation encountered a concurrent update",
                    ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                    kv("profileId", profileId), kv("actorEntraOid", actorOid),
                    kv("outcome", CcmsProfileSyncOutcome.PERSISTENCE_FAILURE), ex);
            return CcmsProfileSyncOutcome.PERSISTENCE_FAILURE;
        } catch (DataAccessException | TransactionException ex) {
            log.error("CCMS profile synchronisation persistence failed",
                    ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                    kv("profileId", profileId), kv("actorEntraOid", actorOid),
                    kv("outcome", CcmsProfileSyncOutcome.PERSISTENCE_FAILURE), ex);
            return CcmsProfileSyncOutcome.PERSISTENCE_FAILURE;
        } catch (RuntimeException ex) {
            log.error("Unexpected CCMS profile synchronisation failure",
                    ExceptionLogFields.exceptionGroup(ex), ExceptionLogFields.exceptionReason(ex),
                    kv("profileId", profileId), kv("actorEntraOid", actorOid),
                    kv("outcome", CcmsProfileSyncOutcome.UNEXPECTED_FAILURE), ex);
            return CcmsProfileSyncOutcome.UNEXPECTED_FAILURE;
        }
    }
}
