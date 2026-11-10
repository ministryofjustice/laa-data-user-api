package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult;
import uk.gov.justice.laa.datauserapi.application.command.dto.BulkDeactivationResult.Status;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.BulkDeactivateFirmUsersCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.FirmNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkDeactivateFirmUsersServiceTest {

    @Mock
    private FirmCommandRepository firmRepository;
    @Mock
    private EntraUserCommandRepository userRepository;
    @Mock
    private DeactivateUserService deactivateService;

    private BulkDeactivateFirmUsersService service;
    private final BulkDeactivateFirmUsersCommand command =
            new BulkDeactivateFirmUsersCommand("FIRM1", DeactivateUserReason.FirmClosureorMerger);
    private final AuthenticatedEntraUser actor = AuthenticatedEntraUser.builder().build();

    @BeforeEach
    void setUp() {
        service = new BulkDeactivateFirmUsersService(
                firmRepository, userRepository, deactivateService);
    }

    @Test
    void deactivatesEveryListedUserWithTheSameActorAndReason() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(firmRepository.existsByCode(command.firmId())).thenReturn(true);
        when(userRepository.findUserEntraOidsByFirmCode(command.firmId()))
                .thenReturn(List.of(firstId.toString(), secondId.toString()));
        when(deactivateService.deactivate(any(), eq(command.deactivateReason()), eq(actor)))
                .thenReturn(CommandResult.success("done"));

        BulkDeactivationResult result = service.deactivate(command, actor);

        assertThat(result.deactivated()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(deactivateService, times(2)).deactivate(captor.capture(), eq(command.deactivateReason()), eq(actor));
        assertThat(captor.getAllValues()).containsExactly(firstId, secondId);
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {
        "ACCESS_DENIED", "USER_NOT_FOUND", "DOWNSTREAM_FAILURE", "SYSTEM_FAILURE", "REJECTED"
    })
    void classifiesFailureAndContinuesWithTheNextUser(Status failureType) {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(firmRepository.existsByCode(command.firmId())).thenReturn(true);
        when(userRepository.findUserEntraOidsByFirmCode(command.firmId()))
                .thenReturn(List.of(firstId.toString(), secondId.toString()));
        when(deactivateService.deactivate(any(), eq(command.deactivateReason()), eq(actor))).thenAnswer(invocation -> {
            UUID targetId = invocation.getArgument(0);
            if (targetId.equals(secondId)) {
                return CommandResult.success("done");
            }
            return switch (failureType) {
                case ACCESS_DENIED -> throw new AccessDeniedException("denied");
                case USER_NOT_FOUND -> throw new UserNotFoundException("missing");
                case DOWNSTREAM_FAILURE -> throw new TechServicesClientException("timeout");
                case SYSTEM_FAILURE -> throw new IllegalStateException("unexpected");
                case REJECTED -> CommandResult.failure("rejected");
                default -> throw new AssertionError("Unexpected test case");
            };
        });

        BulkDeactivationResult result = service.deactivate(command, actor);

        assertThat(result.deactivated()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.outcomes()).extracting(BulkDeactivationResult.UserOutcome::status)
                .containsExactly(failureType, Status.DEACTIVATED);
        verify(deactivateService, times(2)).deactivate(any(), eq(command.deactivateReason()), eq(actor));
    }

    @Test
    void classifiesInvalidAndMissingIdsWithoutCallingWorkflowForThem() {
        String validId = UUID.randomUUID().toString();
        when(firmRepository.existsByCode(command.firmId())).thenReturn(true);
        when(userRepository.findUserEntraOidsByFirmCode(command.firmId()))
                .thenReturn(Arrays.asList("invalid", null, validId));
        when(deactivateService.deactivate(any(), eq(command.deactivateReason()), eq(actor)))
                .thenReturn(CommandResult.success("done"));

        BulkDeactivationResult result = service.deactivate(command, actor);

        assertThat(result.outcomes()).extracting(BulkDeactivationResult.UserOutcome::status)
                .containsExactly(Status.INVALID_USER_ID, Status.INVALID_USER_ID, Status.DEACTIVATED);
        assertThat(result.failed()).isEqualTo(2);
        verify(deactivateService).deactivate(UUID.fromString(validId), command.deactivateReason(), actor);
    }

    @Test
    void emptyFirmHasNoFailuresAndMakesNoPerUserCalls() {
        when(firmRepository.existsByCode(command.firmId())).thenReturn(true);
        when(userRepository.findUserEntraOidsByFirmCode(command.firmId())).thenReturn(List.of());

        assertThat(service.deactivate(command, actor).outcomes()).isEmpty();
        verifyNoInteractions(deactivateService);
    }

    @Test
    void rejectsMissingFirmBeforeLookingUpUsers() {
        assertThatThrownBy(() -> service.deactivate(command, actor))
                .isInstanceOf(FirmNotFoundException.class)
                .hasMessage("Firm not found with ID: " + command.firmId());
        verifyNoInteractions(userRepository, deactivateService);
    }

    @Test
    void asyncRunProcessesUsersAndNeverPropagatesFailures() {
        when(firmRepository.existsByCode(command.firmId())).thenReturn(true);
        when(userRepository.findUserEntraOidsByFirmCode(command.firmId()))
                .thenThrow(new IllegalStateException("database unavailable"));

        service.deactivateAsync(command, actor);

        verifyNoInteractions(deactivateService);
    }

    @Test
    void asyncRunDeactivatesListedUsers() {
        UUID id = UUID.randomUUID();
        when(firmRepository.existsByCode(command.firmId())).thenReturn(true);
        when(userRepository.findUserEntraOidsByFirmCode(command.firmId())).thenReturn(List.of(id.toString()));
        when(deactivateService.deactivate(id, command.deactivateReason(), actor))
                .thenReturn(CommandResult.success("done"));

        service.deactivateAsync(command, actor);

        verify(deactivateService).deactivate(id, command.deactivateReason(), actor);
    }

    @Test
    void asyncMethodRunsOnTheBulkExecutor() throws NoSuchMethodException {
        org.springframework.scheduling.annotation.Async async = BulkDeactivateFirmUsersService.class
                .getMethod("deactivateAsync", BulkDeactivateFirmUsersCommand.class, AuthenticatedEntraUser.class)
                .getAnnotation(org.springframework.scheduling.annotation.Async.class);
        assertThat(async.value()).isEqualTo("bulkDeactivationExecutor");
    }
}
