package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import uk.gov.justice.laa.datauserapi.application.command.dto.UserAccountCreationData;
import uk.gov.justice.laa.datauserapi.application.command.mapper.UserAccountCreationMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserCreationRepository;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResponse;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountCreationServiceTest {
    @Mock private UserCreationRepository repository;
    @Mock private FirmCommandRepository firmRepository;
    @Mock private TechServicesClient client;
    @Mock private UserAccountPersistenceService persistence;
    private UserAccountCreationService service;
    private final String actor = UUID.randomUUID().toString();
    private final UUID firm = UUID.randomUUID();
    private final UUID identity = UUID.randomUUID();
    private final UserAccountCreationMapper mapper = new UserAccountCreationMapper();
    private final CreateUserAccountCommand command = new CreateUserAccountCommand(
            "person@example.com", "First", "Last", "EXTERNAL", true, firm.toString());

    @BeforeEach
    void setUp() {
        service = new UserAccountCreationService(repository, firmRepository, client, persistence, mapper);
    }

    private void provision() {
        when(firmRepository.existsByIdAndEnabledTrue(firm)).thenReturn(true);
        when(client.createUser(any())).thenReturn(new TechServicesClient.Identity(identity, true));
    }

    @Test
    void rejectsUnknownFirmWithoutCallingDownstream() {
        when(repository.findExisting("person@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOfSatisfying(CreateUserException.class,
                ex -> {
                    assertThat(ex.status().value()).isEqualTo(400);
                    assertThat(ex.field()).isEqualTo("firmId");
                });
        verifyNoInteractions(client, persistence);
    }

    @Test
    void returnsExistingLocalIdsWithoutCallingDownstream() {
        var existing = new CreateUserAccountResponse(false, "User already exists", identity.toString(), UUID.randomUUID());
        when(repository.findExisting("person@example.com")).thenReturn(
                Optional.of(new UserAccountCreationData(identity.toString(), existing.userProfileId())));
        assertThatThrownBy(() -> service.create(command, actor))
                .isInstanceOfSatisfying(UserAlreadyExistsException.class, ex -> assertThat(ex.result()).isEqualTo(existing));
        verifyNoInteractions(client, persistence);
    }

    @Test
    void provisionsIdentityAndCommitsBothLocalRecords() {
        provision();
        var expected = new CreateUserAccountResponse(true, "User created", identity.toString(), UUID.randomUUID());
        when(persistence.create(command, identity, actor)).thenReturn(expected);

        assertThat(service.create(command, actor)).isEqualTo(expected);
        var order = inOrder(client, persistence);
        order.verify(client).createUser(new TechServicesClient.Registration("person@example.com", "First", "Last"));
        order.verify(persistence).create(command, identity, actor);
    }

    @Test
    void existingDownstreamIdentityCreatesLocalRecordsWhenOidIsNotStored() {
        when(firmRepository.existsByIdAndEnabledTrue(firm)).thenReturn(true);
        when(client.createUser(any())).thenReturn(new TechServicesClient.Identity(identity, false));
        var expected = new CreateUserAccountResponse(true, "User created", identity.toString(), UUID.randomUUID());
        when(persistence.create(command, identity, actor)).thenReturn(expected);

        assertThat(service.create(command, actor)).isEqualTo(expected);
        verify(persistence).create(command, identity, actor);
    }

    @Test
    void existingDownstreamIdentityWithLocalOidProducesConflictWithoutLocalWrites() {
        when(firmRepository.existsByIdAndEnabledTrue(firm)).thenReturn(true);
        when(client.createUser(any())).thenReturn(new TechServicesClient.Identity(identity, false));
        var existing = new UserAccountCreationData(identity.toString(), UUID.randomUUID());
        when(repository.findExistingByEntraOid(identity)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(command, actor))
                .isInstanceOfSatisfying(UserAlreadyExistsException.class, ex -> {
                    assertThat(ex.result().success()).isFalse();
                    assertThat(ex.result().userEntraObjectId()).isEqualTo(identity.toString());
                    assertThat(ex.result().userProfileId()).isEqualTo(existing.profileId());
                });
        verifyNoInteractions(persistence);
    }

    @Test
    void rollsBackLateConflictAndRetainsProvisionedIdentity() {
        provision();
        var existing = new CreateUserAccountResponse(false, "User already exists",
                UUID.randomUUID().toString(), UUID.randomUUID());
        when(persistence.create(any(), any(), any())).thenThrow(new UserAlreadyExistsException(existing));
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOf(UserAlreadyExistsException.class);
        verify(client).createUser(any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void rollsBackConstraintFailureWithoutDeletingIdentity() {
        provision();
        when(persistence.create(any(), any(), any())).thenThrow(new DataIntegrityViolationException("private database error"));
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOfSatisfying(CreateUserException.class,
                ex -> assertThat(ex.status().value()).isEqualTo(500));
        verifyNoMoreInteractions(client);
    }

    @Test
    void commitFailureIsReportedAsFailureRatherThanSuccess() {
        provision();
        when(persistence.create(any(), any(), any())).thenThrow(new TransactionSystemException("commit failed"));
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOfSatisfying(CreateUserException.class,
                ex -> assertThat(ex.status().value()).isEqualTo(500));
        verifyNoMoreInteractions(client);
    }
}
