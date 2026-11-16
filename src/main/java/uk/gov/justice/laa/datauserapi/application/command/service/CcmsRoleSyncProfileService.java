package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.RetryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsProfileSyncOutcome;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class CcmsRoleSyncProfileService {
    private final CcmsRoleSyncPersistenceService persistence;
    private final CcmsSyncSender sender;
    private final RetryOperations retry;
    private final Clock clock;

    public CcmsRoleSyncProfileService(CcmsRoleSyncPersistenceService persistence, CcmsSyncSender sender,
                                      @Qualifier("ccmsRoleSyncRetry") RetryOperations retry,
                                      @Qualifier("ccmsRoleSyncClock") Clock clock) {
        this.persistence = persistence;
        this.sender = sender;
        this.retry = retry;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CcmsProfileSyncOutcome syncProfile(UUID id, String actorOid) {
        var outcome = CcmsProfileSyncOutcome.SUCCESS;
        CcmsSyncProfile profile;
        try {
            var snapshot = persistence.load(id);
            if (snapshot.isEmpty()) {
                log.info("Skipping deleted CCMS profile", kv("profileId", id), kv("actorEntraOid", actorOid));
                return CcmsProfileSyncOutcome.SKIPPED;
            }
            profile = snapshot.get();
        } catch (CcmsSyncException ex) {
            log.warn("CCMS profile data is invalid", ExceptionLogFields.exceptionGroup(ex),
                    ExceptionLogFields.exceptionReason(ex), kv("profileId", id), kv("actorEntraOid", actorOid), ex);
            return recordResult(id, actorOid, CcmsProfileSyncOutcome.INVALID_DATA);
        }

        var timestamp = LocalDateTime.now(clock);
        for (var entry : profile.rolesByApplication().entrySet()) {
            try {
                retry.execute(context -> {
                    sender.sendSyncMessage(profile, entry.getKey(), entry.getValue(), timestamp);
                    return null;
                });
            } catch (CcmsSyncException ex) {
                log.warn("CCMS role synchronisation failed", ExceptionLogFields.exceptionGroup(ex),
                        ExceptionLogFields.exceptionReason(ex), kv("profileId", id),
                        kv("applicationEntraOid", entry.getKey()), kv("actorEntraOid", actorOid), ex);
                if (outcome != CcmsProfileSyncOutcome.UNEXPECTED_FAILURE) {
                    outcome = CcmsProfileSyncOutcome.DOWNSTREAM_FAILURE;
                }
            } catch (RuntimeException ex) {
                // This per-application boundary keeps independent deliveries running after a programming fault.
                log.error("Unexpected CCMS role synchronisation failure", ExceptionLogFields.exceptionGroup(ex),
                        ExceptionLogFields.exceptionReason(ex), kv("profileId", id),
                        kv("applicationEntraOid", entry.getKey()), kv("actorEntraOid", actorOid), ex);
                outcome = CcmsProfileSyncOutcome.UNEXPECTED_FAILURE;
            }
        }
        return recordResult(id, actorOid, outcome);
    }

    private CcmsProfileSyncOutcome recordResult(UUID id, String actorOid, CcmsProfileSyncOutcome outcome) {
        if (!persistence.recordResult(id, outcome == CcmsProfileSyncOutcome.SUCCESS, actorOid)) {
            log.info("CCMS profile was deleted before recording sync result",
                    kv("profileId", id), kv("actorEntraOid", actorOid));
            return CcmsProfileSyncOutcome.SKIPPED;
        }
        return outcome;
    }
}
