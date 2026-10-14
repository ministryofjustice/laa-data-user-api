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
import org.springframework.security.oauth2.jwt.Jwt;
import uk.gov.justice.laa.datauserapi.application.command.handler.DeactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.handler.ReactivateUserHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ReactivateUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.DeactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.ReactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.InvalidActorContextException;

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
                deactivateUserMapper
        );
    }

    @Nested
    @DisplayName("reactivateUser tests")
    class ReactivateUserTests {

        @Test
        @DisplayName("Should successfully reactivate user when JWT with subject is present")
        void reactivateUser_WithValidJwtSubject_ReturnsOk() {
            // Arrange
            String actorOid = UUID.randomUUID().toString();
            AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                    .entraOid(actorOid)
                    .build();
            when(reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest)).thenReturn(reactivateUserCommand);
            when(reactivateUserHandler.handle(reactivateUserCommand, actorOid)).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.reactivateUser(reactivateUserRequest, user);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(reactivateUserMapper).toReactivateUserCommand(reactivateUserRequest);
            verify(reactivateUserHandler).handle(reactivateUserCommand, actorOid);
            verifyNoInteractions(deactivateUserHandler, deactivateUserMapper);
        }

        @Test
        @DisplayName("Should propagate exception when handler throws exception")
        void reactivateUser_WhenHandlerFails_PropagatesException() {
            // Arrange
            String actorOid = UUID.randomUUID().toString();
            AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                    .entraOid(actorOid)
                    .build();
            when(reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest)).thenReturn(reactivateUserCommand);
            when(reactivateUserHandler.handle(reactivateUserCommand, actorOid))
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
            String actorOid = UUID.randomUUID().toString();
            AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                    .entraOid(actorOid)
                    .build();
            when(deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest)).thenReturn(deactivateUserCommand);
            when(deactivateUserHandler.handle(deactivateUserCommand, actorOid)).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.deactivateUser(deactivateUserRequest, user);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(deactivateUserMapper).toDeactivateUserCommand(deactivateUserRequest);
            verify(deactivateUserHandler).handle(deactivateUserCommand, actorOid);
            verifyNoInteractions(reactivateUserHandler, reactivateUserMapper);
        }

        @Test
        @DisplayName("Should propagate exception when handler throws exception")
        void deactivateUser_WhenHandlerFails_PropagatesException() {
            // Arrange
            String actorOid = UUID.randomUUID().toString();
            AuthenticatedEntraUser user = AuthenticatedEntraUser.builder()
                    .entraOid(actorOid)
                    .build();
            when(deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest)).thenReturn(deactivateUserCommand);
            when(deactivateUserHandler.handle(deactivateUserCommand, actorOid))
                    .thenThrow(new IllegalArgumentException("Deactivation reason required"));

            // Act & Assert
            assertThatThrownBy(() -> controller.deactivateUser(deactivateUserRequest, user))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Deactivation reason required");
        }
    }
}
