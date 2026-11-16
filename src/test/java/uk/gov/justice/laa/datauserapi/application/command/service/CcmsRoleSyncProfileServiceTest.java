package uk.gov.justice.laa.datauserapi.application.command.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.retry.support.RetryTemplate;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsProfileSyncOutcome;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CcmsRoleSyncProfileServiceTest {
    private final CcmsRoleSyncPersistenceService persistence = mock(CcmsRoleSyncPersistenceService.class);
    private final CcmsSyncSender sender = mock(CcmsSyncSender.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
    private final CcmsRoleSyncProfileService service = new CcmsRoleSyncProfileService(persistence, sender,
            RetryTemplate.builder().maxAttempts(3).noBackoff().retryOn(CcmsSyncException.class).build(), clock);
    private final UUID id = UUID.randomUUID();
    private final LocalDateTime timestamp = LocalDateTime.now(clock);
    private final CcmsSyncProfile profile = new CcmsSyncProfile(id, "oid", UUID.randomUUID(),
            "First", "Last", "email@example.test", UserType.INTERNAL, null,
            Map.of("a", Set.of("one"), "b", Set.of("two")));
    private final Logger logger = (Logger) LoggerFactory.getLogger(CcmsRoleSyncProfileService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logs.start();
        logger.addAppender(logs);
        when(persistence.load(id)).thenReturn(Optional.of(profile));
        when(persistence.recordResult(eq(id), anyBoolean(), eq("actor"))).thenReturn(true);
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void sendsSnapshotAndPersistsSuccess() {
        assertThat(service.syncProfile(id, "actor")).isEqualTo(CcmsProfileSyncOutcome.SUCCESS);
        verify(sender).sendSyncMessage(profile, "a", Set.of("one"), timestamp);
        verify(sender).sendSyncMessage(profile, "b", Set.of("two"), timestamp);
        verify(persistence).recordResult(id, true, "actor");
    }

    @Test
    void retriesFailuresAndAttemptsOtherApplicationsBeforeUpdatingFlag() {
        var failure = new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_TIMEOUT);
        doThrow(failure).when(sender).sendSyncMessage(profile, "a", Set.of("one"), timestamp);

        assertThat(service.syncProfile(id, "actor")).isEqualTo(CcmsProfileSyncOutcome.DOWNSTREAM_FAILURE);
        verify(sender, times(3)).sendSyncMessage(profile, "a", Set.of("one"), timestamp);
        verify(sender).sendSyncMessage(profile, "b", Set.of("two"), timestamp);
        verify(persistence).recordResult(id, false, "actor");
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getArgumentArray()).extracting(Object::toString)
                    .contains("exception_group=ccms_sync", "exception_reason=downstream_timeout",
                            "profileId=" + id, "applicationEntraOid=a", "actorEntraOid=actor");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void transientFailureCanSucceedOnRetry() {
        doThrow(new CcmsSyncException(CcmsSyncException.Reason.DOWNSTREAM_UNAVAILABLE)).doNothing()
                .when(sender).sendSyncMessage(profile, "a", Set.of("one"), timestamp);

        assertThat(service.syncProfile(id, "actor")).isEqualTo(CcmsProfileSyncOutcome.SUCCESS);
        verify(sender, times(2)).sendSyncMessage(profile, "a", Set.of("one"), timestamp);
        verify(persistence).recordResult(id, true, "actor");
        assertThat(logs.list).isEmpty();
    }

    @Test
    void unexpectedApplicationFailureIsNotRetriedAndOtherApplicationsContinue() {
        doThrow(new IllegalStateException("unexpected")).when(sender)
                .sendSyncMessage(profile, "a", Set.of("one"), timestamp);

        assertThat(service.syncProfile(id, "actor")).isEqualTo(CcmsProfileSyncOutcome.UNEXPECTED_FAILURE);
        verify(sender).sendSyncMessage(profile, "a", Set.of("one"), timestamp);
        verify(sender).sendSyncMessage(profile, "b", Set.of("two"), timestamp);
        verify(persistence).recordResult(id, false, "actor");
    }

    @Test
    void invalidProfilePersistsFailedResultWithoutSending() {
        when(persistence.load(id)).thenThrow(new CcmsSyncException(CcmsSyncException.Reason.INVALID_PROFILE));

        assertThat(service.syncProfile(id, "actor")).isEqualTo(CcmsProfileSyncOutcome.INVALID_DATA);
        verify(persistence).recordResult(id, false, "actor");
        verifyNoInteractions(sender);
    }

    @Test
    void deletionDuringNotificationIsSkipped() {
        when(persistence.recordResult(id, true, "actor")).thenReturn(false);
        assertThat(service.syncProfile(id, "actor")).isEqualTo(CcmsProfileSyncOutcome.SKIPPED);
    }

    @Test
    void deletedProfileDoesNotSendOrPersist() {
        when(persistence.load(id)).thenReturn(Optional.empty());
        assertThat(service.syncProfile(id, "actor")).isEqualTo(CcmsProfileSyncOutcome.SKIPPED);
        verifyNoInteractions(sender);
        verify(persistence, times(0)).recordResult(eq(id), anyBoolean(), eq("actor"));
    }

    @Test
    void persistenceFailurePropagatesToBatchBoundary() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(persistence.recordResult(id, true, "actor")).thenThrow(failure);
        assertThatThrownBy(() -> service.syncProfile(id, "actor")).isSameAs(failure);
    }
}
