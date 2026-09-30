package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.request.ReactivateUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReactivateUserMapperTest {

    private ReactivateUserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ReactivateUserMapper();
    }

    @Nested
    @DisplayName("toAuditEntity tests")
    class ToAuditEntityTests {

        @Test
        @DisplayName("Should correctly map EntraUser and metadata to UserAccountStatusAudit")
        void toAuditEntity_MapsAllFieldsCorrectly() {
            // Arrange
            String actorId = "admin-oid-456";
            String comments = "User account reactivated following review";

            EntraUser user = EntraUser.builder()
                    .id(UUID.randomUUID())
                    .entraOid("entra-oid-123")
                    .email("john.doe@justice.gov.uk")
                    .firstName("John")
                    .lastName("Doe")
                    .build();

            LocalDateTime testStartTime = LocalDateTime.now();

            // Act
            UserAccountStatusAudit audit = mapper.toAuditEntity(user, actorId, comments);

            // Assert
            assertThat(audit).isNotNull();
            assertThat(audit.getEntraUser()).isSameAs(user);
            assertThat(audit.getUserEmail()).isEqualTo("john.doe@justice.gov.uk");
            assertThat(audit.getUserName()).isEqualTo("John Doe");
            assertThat(audit.getUserAccountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
            assertThat(audit.getStatusChangedBy()).isEqualTo(actorId);
            assertThat(audit.getComments()).isEqualTo(comments);
            assertThat(audit.getDeactivateUserReasonLookup()).isNull();
            assertThat(audit.getStatusChangedDate())
                    .isNotNull()
                    .isCloseTo(testStartTime, within(2, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Should allow null comments when mapping to UserAccountStatusAudit")
        void toAuditEntity_WithNullComments_MapsSuccessfully() {
            // Arrange
            EntraUser user = EntraUser.builder()
                    .id(UUID.randomUUID())
                    .entraOid("entra-oid-789")
                    .email("jane.smith@justice.gov.uk")
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            // Act
            UserAccountStatusAudit audit = mapper.toAuditEntity(user, "system", null);

            // Assert
            assertThat(audit).isNotNull();
            assertThat(audit.getComments()).isNull();
            assertThat(audit.getUserName()).isEqualTo("Jane Smith");
            assertThat(audit.getUserAccountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("toCommandResult tests")
    class ToCommandResultTests {

        @Test
        @DisplayName("Should generate success CommandResult containing Entra OID")
        void toCommandResult_WithValidUser_ReturnsFormattedSuccessResult() {
            // Arrange
            String entraOid = "55555555-5555-5555-5555-555555555555";
            EntraUser user = EntraUser.builder()
                    .id(UUID.randomUUID())
                    .entraOid(entraOid)
                    .build();

            // Act
            CommandResult result = mapper.toCommandResult(user);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.success()).isTrue();
            assertThat(result.message())
                    .isEqualTo(String.format("User account '%s' reactivated successfully", entraOid));
        }
    }

    @Nested
    @DisplayName("toReactivateUserCommand tests")
    class ToReactivateUserCommandTests {

        @Test
        @DisplayName("Should map ReactivateUserRequest fields to ReactivateUserCommand")
        void toReactivateUserCommand_MapsFieldsAccurately() {
            // Arrange
            UUID userEntraObjectId = UUID.randomUUID();
            String comments = "Account unlocked per ticket #12345";

            ReactivateUserRequest request = new ReactivateUserRequest(userEntraObjectId, comments);

            // Act
            ReactivateUserCommand command = mapper.toReactivateUserCommand(request);

            // Assert
            assertThat(command).isNotNull();
            assertThat(command.userEntraObjectId()).isEqualTo(userEntraObjectId);
            assertThat(command.comments()).isEqualTo(comments);
        }

        @Test
        @DisplayName("Should correctly handle request with null comments")
        void toReactivateUserCommand_WithNullComments_MapsAccurately() {
            // Arrange
            UUID userEntraObjectId = UUID.randomUUID();
            ReactivateUserRequest request = new ReactivateUserRequest(userEntraObjectId, null);

            // Act
            ReactivateUserCommand command = mapper.toReactivateUserCommand(request);

            // Assert
            assertThat(command).isNotNull();
            assertThat(command.userEntraObjectId()).isEqualTo(userEntraObjectId);
            assertThat(command.comments()).isNull();
        }
    }
}
