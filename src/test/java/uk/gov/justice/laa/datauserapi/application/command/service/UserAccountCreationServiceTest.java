package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountCreationServiceTest {
    @Mock private UserCreationRepository repository;
    @Mock private TechServicesClient client;
    @Mock private UserAccountPersistenceService persistence;
    private UserAccountCreationService service;
    private final String actor = UUID.randomUUID().toString();
    private final UUID firm = UUID.randomUUID();
    private final UUID identity = UUID.randomUUID();
    private final CreateUserAccountCommand command = new CreateUserAccountCommand(
            "Person@Example.com", "First", "Last", "EXTERNAL", true, firm.toString());

    @BeforeEach
    void setUp() {
        service = new UserAccountCreationService(repository, client, persistence);
    }

    private void provision() {
        when(repository.firmExists(firm)).thenReturn(true);
        when(client.createUser(any())).thenReturn(new TechServicesClient.Identity(identity, true));
    }

    @Test
    void rejectsUnknownFirmWithoutCallingDownstream() {
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOfSatisfying(CreateUserException.class,
                ex -> {
                    assertThat(ex.status().value()).isEqualTo(400);
                    assertThat(ex.field()).isEqualTo("firmId");
                });
        verifyNoInteractions(client, persistence);
    }

    @Test
    void returnsExistingLocalIdsWithoutCallingDownstream() {
        var existing = new CreateUserAccountResponse(false, "Already exists", identity.toString(), UUID.randomUUID());
        when(repository.findExisting("person@example.com")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(command, actor))
                .isInstanceOfSatisfying(UserAlreadyExistsException.class, ex -> assertThat(ex.result()).isEqualTo(existing));
        verifyNoInteractions(client, persistence);
    }

    @Test
    void normalizesEmailAndCommitsBothLocalRecords() {
        provision();
        var normalized = new CreateUserAccountCommand("person@example.com", "First", "Last", "EXTERNAL", true, firm.toString());
        var expected = new CreateUserAccountResponse(true, "Created", identity.toString(), UUID.randomUUID());
        when(persistence.create(normalized, identity, actor)).thenReturn(expected);

        assertThat(service.create(command, actor)).isEqualTo(expected);
        var order = inOrder(client, persistence);
        order.verify(client).createUser(new TechServicesClient.Registration("person@example.com", "First", "Last"));
        order.verify(persistence).create(normalized, identity, actor);
    }

    @Test
    void existingDownstreamIdentityProducesConflictWithoutLocalWrites() {
        when(repository.firmExists(firm)).thenReturn(true);
        when(client.createUser(any())).thenReturn(new TechServicesClient.Identity(identity, false));
        assertThatThrownBy(() -> service.create(command, actor))
                .isInstanceOfSatisfying(UserAlreadyExistsException.class, ex -> {
                    assertThat(ex.result().success()).isFalse();
                    assertThat(ex.result().userEntraObjectId()).isEqualTo(identity.toString());
                    assertThat(ex.result().userProfileId()).isNull();
                });
        verifyNoInteractions(persistence);
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void rollsBackLateConflictAndRetainsProvisionedIdentity() {
        provision();
        var existing = new CreateUserAccountResponse(false, "Already exists", UUID.randomUUID().toString(), UUID.randomUUID());
        when(persistence.create(any(), any(), any())).thenThrow(new UserAlreadyExistsException(existing));
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOf(UserAlreadyExistsException.class);
        verify(repository, never()).insert(any(), any(), any());
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
