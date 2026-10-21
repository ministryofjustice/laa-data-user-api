package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserCreationRepository;
import uk.gov.justice.laa.datauserapi.client.ts.EntraUserClient;
import uk.gov.justice.laa.datauserapi.contracts.request.CreateUserAccountCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CreateUserAccountResult;
import uk.gov.justice.laa.datauserapi.exception.CreateUserException;
import uk.gov.justice.laa.datauserapi.exception.UserAlreadyExistsException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountCreationServiceTest {
    @Mock private UserCreationRepository repository;
    @Mock private EntraUserClient client;
    @Mock private PlatformTransactionManager manager;
    private UserAccountCreationService service;
    private final String actor = UUID.randomUUID().toString();
    private final UUID firm = UUID.randomUUID();
    private final UUID identity = UUID.randomUUID();
    private final CreateUserAccountCommand command = new CreateUserAccountCommand(
            "Person@Example.com", "First", "Last", "EXTERNAL", true, firm.toString());

    @BeforeEach
    void setUp() {
        service = new UserAccountCreationService(repository, client, manager);
    }

    private void permit() {
        when(repository.canCreateExternalUser(actor)).thenReturn(true);
    }

    private void provision() {
        permit();
        when(repository.firmExists(firm)).thenReturn(true);
        when(client.createUser(any())).thenReturn(new EntraUserClient.Identity(identity, true));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void deniesUnknownActorBeforeProvisioning() {
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(client, manager);
        verify(repository, never()).findExisting(any());
    }

    @Test
    void deniesMissingOidWithoutUsingSubjectOrClaims() {
        assertThatThrownBy(() -> service.create(command, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository, client, manager);
    }

    @Test
    void rejectsUnknownFirmWithoutCallingDownstream() {
        permit();
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOfSatisfying(CreateUserException.class,
                ex -> {
                    assertThat(ex.status().value()).isEqualTo(400);
                    assertThat(ex.field()).isEqualTo("firmId");
                });
        verifyNoInteractions(client, manager);
    }

    @Test
    void returnsExistingLocalIdsWithoutCallingDownstream() {
        permit();
        var existing = new CreateUserAccountResult(false, "Already exists", identity.toString(), UUID.randomUUID());
        when(repository.findExisting("person@example.com")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(command, actor))
                .isInstanceOfSatisfying(UserAlreadyExistsException.class, ex -> assertThat(ex.result()).isEqualTo(existing));
        verifyNoInteractions(client, manager);
    }

    @Test
    void normalizesEmailAndCommitsBothLocalRecords() {
        provision();
        var normalized = new CreateUserAccountCommand("person@example.com", "First", "Last", "EXTERNAL", true, firm.toString());
        var expected = new CreateUserAccountResult(true, "Created", identity.toString(), UUID.randomUUID());
        when(repository.insert(normalized, identity, actor)).thenReturn(expected);

        assertThat(service.create(command, actor)).isEqualTo(expected);
        var order = inOrder(client, manager, repository);
        order.verify(client).createUser(normalized);
        order.verify(manager).getTransaction(any());
        order.verify(repository).lockEmail(normalized.email());
        order.verify(repository).findExisting(normalized.email());
        order.verify(repository).insert(normalized, identity, actor);
        order.verify(manager).commit(any());
    }

    @Test
    void existingDownstreamIdentityProducesConflictWithoutLocalWrites() {
        permit();
        when(repository.firmExists(firm)).thenReturn(true);
        when(client.createUser(any())).thenReturn(new EntraUserClient.Identity(identity, false));
        assertThatThrownBy(() -> service.create(command, actor))
                .isInstanceOfSatisfying(UserAlreadyExistsException.class, ex -> {
                    assertThat(ex.result().success()).isFalse();
                    assertThat(ex.result().userEntraObjectId()).isEqualTo(identity.toString());
                    assertThat(ex.result().userProfileId()).isNull();
                });
        verifyNoInteractions(manager);
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void rollsBackLateConflictAndRetainsProvisionedIdentity() {
        provision();
        var existing = new CreateUserAccountResult(false, "Already exists", UUID.randomUUID().toString(), UUID.randomUUID());
        when(repository.findExisting(any())).thenReturn(Optional.empty(), Optional.of(existing));
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOf(UserAlreadyExistsException.class);
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
        verify(repository, never()).insert(any(), any(), any());
        verify(client).createUser(any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void rollsBackConstraintFailureWithoutDeletingIdentity() {
        provision();
        when(repository.insert(any(), any(), any())).thenThrow(new DataIntegrityViolationException("private database error"));
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOfSatisfying(CreateUserException.class,
                ex -> assertThat(ex.status().value()).isEqualTo(500));
        verify(manager).rollback(any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void commitFailureIsReportedAsFailureRatherThanSuccess() {
        provision();
        doThrow(new TransactionSystemException("commit failed")).when(manager).commit(any());
        assertThatThrownBy(() -> service.create(command, actor)).isInstanceOfSatisfying(CreateUserException.class,
                ex -> assertThat(ex.status().value()).isEqualTo(500));
        verifyNoMoreInteractions(client);
    }
}
