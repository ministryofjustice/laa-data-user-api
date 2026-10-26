package uk.gov.justice.laa.datauserapi.application.command.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeleteUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.controller.mapper.ConvertToMultiFirmCommandMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmHandler;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.DeactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.DeleteUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.ReactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountCommandControllerTest {

    @Mock
    private ReactivateUserHandler reactivateUserHandler;

    @Mock
    private ReactivateUserMapper reactivateUserMapper;

    @Mock
    private DeactivateUserHandler deactivateUserHandler;

    @Mock
    private DeactivateUserMapper deactivateUserMapper;

    @Mock
    private DeleteUserHandler deleteUserHandler;

    @Mock
    private DeleteUserMapper deleteUserMapper;

    @Mock
    private ConvertToMultiFirmHandler convertToMultiFirmHandler;

    @Mock
    private ConvertToMultiFirmCommandMapper convertToMultiFirmCommandMapper;

    @Mock
    private ReactivateUserRequest reactivateUserRequest;

    @Mock
    private ReactivateUserCommand reactivateUserCommand;

    @Mock
    private DeactivateUserRequest deactivateUserRequest;

    @Mock
    private DeactivateUserCommand deactivateUserCommand;

    @Mock
    private CommandResult commandResult;

    private UserAccountCommandController controller;

    @BeforeEach
    void setUp() {
        controller = new UserAccountCommandController(
                reactivateUserHandler,
                reactivateUserMapper,
                deactivateUserHandler,
                deactivateUserMapper,
                deleteUserHandler,
                deleteUserMapper,
                convertToMultiFirmHandler,
                convertToMultiFirmCommandMapper
        );
    }

    @Nested
    @DisplayName("reactivateUser tests")
    class ReactivateUserTests {

        @Test
        @DisplayName("Should successfully reactivate user when JWT with subject is present")
        void reactivateUser_WithValidJwtSubject_ReturnsOk() {
            // Arrange
            UUID actorId = UUID.randomUUID();
            final AuthenticatedEntraUser actorUser = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .email("actor@example.com")
                    .active(true)
                    .build();
            when(reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest)).thenReturn(reactivateUserCommand);
            when(reactivateUserHandler.handle(reactivateUserCommand, actorUser)).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.reactivateUser(reactivateUserRequest, actorUser);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(reactivateUserMapper).toReactivateUserCommand(reactivateUserRequest);
            verify(reactivateUserHandler).handle(reactivateUserCommand, actorUser);
            verifyNoInteractions(deactivateUserHandler, deactivateUserMapper);
        }

        @Test
        @DisplayName("Should propagate exception when handler throws exception")
        void reactivateUser_WhenHandlerFails_PropagatesException() {
            // Arrange
            UUID actorId = UUID.randomUUID();
            AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .build();
            when(reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest)).thenReturn(reactivateUserCommand);
            when(reactivateUserHandler.handle(reactivateUserCommand, user))
                    .thenThrow(new IllegalStateException("Reactivation failed"));

            // Act & Assert
            assertThatThrownBy(() -> controller.reactivateUser(reactivateUserRequest, user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Reactivation failed");
        }
    }

    @Nested
    @DisplayName("deactivateUser tests")
    class DeactivateUserTests {

        @Test
        @DisplayName("Should successfully deactivate user when JWT with subject is present")
        void deactivateUser_WithValidJwtSubject_ReturnsOk() {
            // Arrange
            UUID actorId = UUID.randomUUID();
            AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .build();
            when(deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest)).thenReturn(deactivateUserCommand);
            when(deactivateUserHandler.handle(deactivateUserCommand, user)).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.deactivateUser(deactivateUserRequest, user);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(deactivateUserMapper).toDeactivateUserCommand(deactivateUserRequest);
            verify(deactivateUserHandler).handle(deactivateUserCommand, user);
            verifyNoInteractions(reactivateUserHandler, reactivateUserMapper);
        }

        @Test
        @DisplayName("Should propagate exception when handler throws exception")
        void deactivateUser_WhenHandlerFails_PropagatesException() {
            // Arrange
            UUID actorId = UUID.randomUUID();
            AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                    .id(actorId)
                    .entraOid(actorId.toString())
                    .build();
            when(deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest)).thenReturn(deactivateUserCommand);
            when(deactivateUserHandler.handle(deactivateUserCommand, user))
                    .thenThrow(new IllegalArgumentException("Deactivation reason required"));

            // Act & Assert
            assertThatThrownBy(() -> controller.deactivateUser(deactivateUserRequest, user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Deactivation reason required");
        }
    }

    @Test
    @DisplayName("deleteUser delegates to the delete handler with the JWT subject as actor")
    void deleteUser_DelegatesToHandler() {
        UUID actorId = UUID.randomUUID();
        AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                .id(actorId)
                .entraOid(actorId.toString())
                .build();
        DeleteUserRequest request = new DeleteUserRequest(
                java.util.UUID.randomUUID(), uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason.LeftFirm);
        DeleteUserCommand command = new DeleteUserCommand(request.userEntraObjectId(), request.deleteReason());
        CommandResult result = CommandResult.success("deleted");
        when(deleteUserMapper.toDeleteUserCommand(request)).thenReturn(command);
        when(deleteUserHandler.handle(command, user)).thenReturn(result);

        ResponseEntity<CommandResult> response = controller.deleteUser(request, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(result);
    }
}
