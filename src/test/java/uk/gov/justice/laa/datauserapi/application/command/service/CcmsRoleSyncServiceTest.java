package uk.gov.justice.laa.datauserapi.application.command.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsProfileSyncOutcome;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.datauserapi.application.command.dto.CcmsProfileSyncOutcome.PERSISTENCE_FAILURE;
import static uk.gov.justice.laa.datauserapi.application.command.dto.CcmsProfileSyncOutcome.SKIPPED;
import static uk.gov.justice.laa.datauserapi.application.command.dto.CcmsProfileSyncOutcome.SUCCESS;
import static uk.gov.justice.laa.datauserapi.application.command.dto.CcmsProfileSyncOutcome.UNEXPECTED_FAILURE;

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
    void countsEveryOutcomeAndContinuesWithoutDuplicatingProfileFailureLogs() {
        var ids = Arrays.stream(CcmsProfileSyncOutcome.values()).map(outcome -> UUID.randomUUID()).toList();
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(ids);
        var outcomes = CcmsProfileSyncOutcome.values();
        for (int i = 0; i < outcomes.length; i++) {
            when(profiles.syncProfile(ids.get(i), "actor")).thenReturn(outcomes[i]);
        }

        service.sync("actor");

        ids.forEach(id -> verify(profiles).syncProfile(id, "actor"));
        assertThat(logs.list).hasSize(2);
        assertSummary(Level.WARN, "processedProfiles=5", "successfulProfiles=1", "unsuccessfulProfiles=4",
                "persistenceFailedProfiles=1", "invalidProfiles=1", "unexpectedFailedProfiles=1", "skippedProfiles=1");
    }

    @ParameterizedTest
    @MethodSource("profileFailures")
    void isolatesProfileFailuresAndLogsThemOnce(RuntimeException failure, Level level,
                                                 CcmsProfileSyncOutcome outcome, String group) {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(List.of(first, second));
        when(profiles.syncProfile(first, "actor")).thenThrow(failure);
        when(profiles.syncProfile(second, "actor")).thenReturn(SUCCESS);

        service.sync("actor");

        verify(profiles).syncProfile(first, "actor");
        verify(profiles).syncProfile(second, "actor");
        assertThat(logs.list).filteredOn(event -> event.getThrowableProxy() != null)
                .singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(level);
                    assertThat(event.getThrowableProxy().getClassName()).isEqualTo(failure.getClass().getName());
                    assertThat(arguments(event)).contains("profileId=" + first, "actorEntraOid=actor",
                            "outcome=" + outcome, "exception_group=" + group, "exception_reason=" + group);
                });
        assertSummary(Level.WARN, "processedProfiles=2", "successfulProfiles=1", "unsuccessfulProfiles=1",
                "persistenceFailedProfiles=" + (outcome == PERSISTENCE_FAILURE ? 1 : 0),
                "unexpectedFailedProfiles=" + (outcome == UNEXPECTED_FAILURE ? 1 : 0), "skippedProfiles=0");
    }

    static Stream<Arguments> profileFailures() {
        return Stream.of(
                Arguments.of(new OptimisticLockingFailureException("Deleted"), Level.WARN,
                        PERSISTENCE_FAILURE, "optimistic_locking_failure"),
                Arguments.of(new CannotAcquireLockException("Concurrent update"), Level.WARN,
                        PERSISTENCE_FAILURE, "cannot_acquire_lock"),
                Arguments.of(new DataIntegrityViolationException("Constraint failed"), Level.ERROR,
                        PERSISTENCE_FAILURE, "data_integrity_violation"),
                Arguments.of(new DataAccessResourceFailureException("Database unavailable"), Level.ERROR,
                        PERSISTENCE_FAILURE, "data_access_resource_failure"),
                Arguments.of(new TransactionSystemException("Transaction failed"), Level.ERROR,
                        PERSISTENCE_FAILURE, "transaction_system"),
                Arguments.of(new IllegalStateException("Unexpected failure"), Level.ERROR,
                        UNEXPECTED_FAILURE, "illegal_state"));
    }

    @ParameterizedTest
    @MethodSource("selectionFailures")
    void propagatesSelectionFailureWithoutAttemptingProfiles(RuntimeException failure) {
        when(repository.findUserProfileIdsForCcmsSync()).thenThrow(failure);

        assertThatThrownBy(() -> service.sync("actor")).isSameAs(failure);

        verifyNoInteractions(profiles);
        assertThat(logs.list).noneMatch(event -> event.getMessage().startsWith("CCMS role sync completed"));
    }

    static Stream<RuntimeException> selectionFailures() {
        return Stream.of(new DataAccessResourceFailureException("Database unavailable"),
                new TransactionSystemException("Transaction failed"), new IllegalStateException("Unexpected failure"));
    }

    @Test
    void countsSuccessAndSkippedProfilesWithoutTreatingSkippedAsProcessed() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(List.of(first, second));
        when(profiles.syncProfile(first, "actor")).thenReturn(SKIPPED);
        when(profiles.syncProfile(second, "actor")).thenReturn(SUCCESS);

        service.sync("actor");

        assertSummary(Level.INFO, "processedProfiles=1", "successfulProfiles=1", "unsuccessfulProfiles=0",
                "skippedProfiles=1");
    }

    @Test
    void noEligibleProfilesDoesNotSendNotifications() {
        when(repository.findUserProfileIdsForCcmsSync()).thenReturn(List.of());

        service.sync("actor");

        verifyNoInteractions(profiles);
        assertSummary(Level.INFO, "processedProfiles=0", "successfulProfiles=0", "unsuccessfulProfiles=0",
                "skippedProfiles=0");
    }

    @Test
    void suspendsInheritedTransactionsAndUsesCcmsExecutor() throws NoSuchMethodException {
        var method = CcmsRoleSyncService.class.getMethod("sync", String.class);
        assertThat(CcmsRoleSyncService.class.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
        assertThat(method.getAnnotation(Async.class).value()).isEqualTo("ccmsRoleSyncExecutor");
    }

    private void assertSummary(Level level, String... fields) {
        assertThat(logs.list).filteredOn(event -> event.getMessage().startsWith("CCMS role sync completed"))
                .singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(level);
                    assertThat(event.getThrowableProxy()).isNull();
                    assertThat(arguments(event)).contains("actorEntraOid=actor").contains(fields);
                });
    }

    private List<String> arguments(ILoggingEvent event) {
        return Arrays.stream(event.getArgumentArray()).filter(argument -> argument != null)
                .map(Object::toString).toList();
    }
}
