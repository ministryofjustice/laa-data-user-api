package uk.gov.justice.laa.datauserapi.application.command.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionSystemException;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.service.CcmsRoleSyncProfileService.ProfileSyncResult;
import uk.gov.justice.laa.datauserapi.exception.CcmsSyncException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CcmsRoleSyncServiceTest {
    private final UserProfileCommandRepository repository = mock(UserProfileCommandRepository.class);
    private final CcmsRoleSyncProfileService profiles = mock(CcmsRoleSyncProfileService.class);
    private final CcmsRoleSyncService service = new CcmsRoleSyncService(repository, profiles);
    private final Logger logger = (Logger) LoggerFactory.getLogger(CcmsRoleSyncService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void continuesAfterDownstreamFailureAndDeletedProfile() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(List.of(first, second, third));
        when(profiles.syncProfile(first, "actor")).thenReturn(new ProfileSyncResult(true,
                new CcmsSyncException(HttpStatus.GATEWAY_TIMEOUT, "Timed out")));
        when(profiles.syncProfile(second, "actor")).thenReturn(new ProfileSyncResult(false, null));
        when(profiles.syncProfile(third, "actor")).thenReturn(new ProfileSyncResult(true, null));

        service.sync("actor");

        verify(profiles).syncProfile(first, "actor");
        verify(profiles).syncProfile(second, "actor");
        verify(profiles).syncProfile(third, "actor");
    }

    @Test
    void countsDownstreamAndPersistenceFailuresTogether() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(List.of(first, second));
        when(profiles.syncProfile(first, "actor")).thenReturn(new ProfileSyncResult(true,
                new CcmsSyncException(HttpStatus.GATEWAY_TIMEOUT, "Timed out")));
        when(profiles.syncProfile(second, "actor")).thenThrow(new OptimisticLockingFailureException("Deleted"));

        service.sync("actor");

        assertThat(logs.list).filteredOn(event -> event.getMessage().startsWith("CCMS role sync completed"))
                .singleElement().satisfies(event ->
                        assertThat(event.getArgumentArray()).filteredOn(argument -> argument != null).extracting(Object::toString)
                                .contains("processedProfiles=2", "successfulProfiles=0",
                                        "unsuccessfulProfiles=2", "persistenceFailedProfiles=1"));
    }

    @ParameterizedTest
    @MethodSource("profileLocalFailures")
    void continuesAfterProfileLocalPersistenceFailure(RuntimeException failure) {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(List.of(first, second));
        when(profiles.syncProfile(first, "actor")).thenThrow(failure);
        when(profiles.syncProfile(second, "actor")).thenReturn(new ProfileSyncResult(true, null));

        service.sync("actor");

        verify(profiles).syncProfile(second, "actor");
        assertThat(logs.list).filteredOn(event -> event.getMessage().startsWith("CCMS profile synchronization transaction failed"))
                .singleElement().satisfies(event -> {
                    assertThat(event.getArgumentArray()).filteredOn(argument -> argument != null).extracting(Object::toString)
                            .contains("profileId=" + first, "actorEntraOid=actor");
                    assertThat(event.getThrowableProxy().getClassName()).isEqualTo(failure.getClass().getName());
                });
        assertThat(logs.list).filteredOn(event -> event.getMessage().startsWith("CCMS role sync completed"))
                .singleElement().satisfies(event -> {
                    assertThat(event.getMessage()).startsWith("CCMS role sync completed with failures");
                    assertThat(event.getArgumentArray()).filteredOn(argument -> argument != null).extracting(Object::toString)
                            .contains("processedProfiles=2", "successfulProfiles=1",
                                    "unsuccessfulProfiles=1", "persistenceFailedProfiles=1");
                });
    }

    static Stream<RuntimeException> profileLocalFailures() {
        return Stream.of(new OptimisticLockingFailureException("Deleted during sync"),
                new CannotAcquireLockException("Concurrent update"),
                new DataIntegrityViolationException("Profile constraint failed"));
    }

    @ParameterizedTest
    @MethodSource("fatalFailures")
    void propagatesInfrastructureAndUnexpectedFailures(RuntimeException failure) {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(List.of(first, second));
        when(profiles.syncProfile(first, "actor")).thenThrow(failure);

        assertThatThrownBy(() -> service.sync("actor")).isSameAs(failure);

        verify(profiles).syncProfile(first, "actor");
        verifyNoMoreInteractions(profiles);
    }

    static Stream<RuntimeException> fatalFailures() {
        return Stream.of(new DataAccessResourceFailureException("Database unavailable"),
                new TransactionSystemException("Transaction infrastructure failed"),
                new IllegalStateException("Unexpected failure"));
    }

    @Test
    void noEligibleProfilesDoesNotSendNotifications() {
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(List.of());
        service.sync("actor");
        verifyNoInteractions(profiles);
    }

}
