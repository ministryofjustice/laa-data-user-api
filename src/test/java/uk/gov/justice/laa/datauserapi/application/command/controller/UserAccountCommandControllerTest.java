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
    private Jwt jwt;

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
            String actorId = "user-entra-sub-123";
            when(jwt.getSubject()).thenReturn(actorId);
            when(reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest)).thenReturn(reactivateUserCommand);
            when(reactivateUserHandler.handle(reactivateUserCommand, actorId)).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.reactivateUser(reactivateUserRequest, jwt);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(reactivateUserMapper).toReactivateUserCommand(reactivateUserRequest);
            verify(reactivateUserHandler).handle(reactivateUserCommand, actorId);
            verifyNoInteractions(deactivateUserHandler, deactivateUserMapper);
        }

        @Test
        @DisplayName("Should fallback actor to 'system' when JWT is null")
        void reactivateUser_WithNullJwt_ResolvesActorAsSystem() {
            // Arrange
            when(reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest)).thenReturn(reactivateUserCommand);
            when(reactivateUserHandler.handle(reactivateUserCommand, "system")).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.reactivateUser(reactivateUserRequest, null);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(reactivateUserHandler).handle(reactivateUserCommand, "system");
        }

        @Test
        @DisplayName("Should fallback actor to 'system' when JWT has null subject")
        void reactivateUser_WithNullJwtSubject_ResolvesActorAsSystem() {
            // Arrange
            when(jwt.getSubject()).thenReturn(null);
            when(reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest)).thenReturn(reactivateUserCommand);
            when(reactivateUserHandler.handle(reactivateUserCommand, "system")).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.reactivateUser(reactivateUserRequest, jwt);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(reactivateUserHandler).handle(reactivateUserCommand, "system");
        }

        @Test
        @DisplayName("Should propagate exception when handler throws exception")
        void reactivateUser_WhenHandlerFails_PropagatesException() {
            // Arrange
            when(jwt.getSubject()).thenReturn("actor-1");
            when(reactivateUserMapper.toReactivateUserCommand(reactivateUserRequest)).thenReturn(reactivateUserCommand);
            when(reactivateUserHandler.handle(reactivateUserCommand, "actor-1"))
                    .thenThrow(new IllegalStateException("Reactivation failed"));

            // Act & Assert
            assertThatThrownBy(() -> controller.reactivateUser(reactivateUserRequest, jwt))
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
            String actorId = "admin-sub-456";
            when(jwt.getSubject()).thenReturn(actorId);
            when(deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest)).thenReturn(deactivateUserCommand);
            when(deactivateUserHandler.handle(deactivateUserCommand, actorId)).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.deactivateUser(deactivateUserRequest, jwt);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(deactivateUserMapper).toDeactivateUserCommand(deactivateUserRequest);
            verify(deactivateUserHandler).handle(deactivateUserCommand, actorId);
            verifyNoInteractions(reactivateUserHandler, reactivateUserMapper);
        }

        @Test
        @DisplayName("Should fallback actor to 'system' when JWT is null")
        void deactivateUser_WithNullJwt_ResolvesActorAsSystem() {
            // Arrange
            when(deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest)).thenReturn(deactivateUserCommand);
            when(deactivateUserHandler.handle(deactivateUserCommand, "system")).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.deactivateUser(deactivateUserRequest, null);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(deactivateUserHandler).handle(deactivateUserCommand, "system");
        }

        @Test
        @DisplayName("Should fallback actor to 'system' when JWT has null subject")
        void deactivateUser_WithNullJwtSubject_ResolvesActorAsSystem() {
            // Arrange
            when(jwt.getSubject()).thenReturn(null);
            when(deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest)).thenReturn(deactivateUserCommand);
            when(deactivateUserHandler.handle(deactivateUserCommand, "system")).thenReturn(commandResult);

            // Act
            ResponseEntity<CommandResult> response = controller.deactivateUser(deactivateUserRequest, jwt);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(commandResult);

            verify(deactivateUserHandler).handle(deactivateUserCommand, "system");
        }

        @Test
        @DisplayName("Should propagate exception when handler throws exception")
        void deactivateUser_WhenHandlerFails_PropagatesException() {
            // Arrange
            when(jwt.getSubject()).thenReturn("actor-2");
            when(deactivateUserMapper.toDeactivateUserCommand(deactivateUserRequest)).thenReturn(deactivateUserCommand);
            when(deactivateUserHandler.handle(deactivateUserCommand, "actor-2"))
                    .thenThrow(new IllegalArgumentException("Deactivation reason required"));

            // Act & Assert
            assertThatThrownBy(() -> controller.deactivateUser(deactivateUserRequest, jwt))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Deactivation reason required");
        }
    }
}
