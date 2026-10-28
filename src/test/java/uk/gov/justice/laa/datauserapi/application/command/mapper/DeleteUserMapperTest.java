package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DeleteUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;
import uk.gov.justice.laa.datauserapi.contracts.request.DeleteUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.DeleteUserReasonEntity;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteUserMapperTest {

    @Mock
    private DeleteUserReasonRepository repository;

    private DeleteUserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DeleteUserMapper(repository);
    }

    @Test
    void mapsRequestToCommand() {
        UUID id = UUID.randomUUID();

        DeleteUserCommand command = mapper.toDeleteUserCommand(new DeleteUserRequest(id, DeleteUserReason.LeftFirm));

        assertThat(command.userEntraObjectId()).isEqualTo(id);
        assertThat(command.deleteReason()).isEqualTo(DeleteUserReason.LeftFirm);
    }

    @Test
    void buildsAuditEntity() {
        UUID id = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.now().minusDays(3);
        EntraUser user = EntraUser.builder().firstName("John").lastName("Doe").email("j@d.com").build();
        user.setId(id);
        user.setCreatedDate(created);
        var reason = DeleteUserReasonEntity.builder().code("LeftFirm").build();
        when(repository.findByCode(DeleteUserReason.LeftFirm.name())).thenReturn(Optional.of(reason));

        UserAccountStatusAudit audit = mapper.toAuditEntity(user,
                new DeleteUserCommand(UUID.randomUUID(), DeleteUserReason.LeftFirm), "actor");

        assertThat(audit.getUserAccountAuditStatus()).isEqualTo(UserAccountAuditStatus.DELETED);
        assertThat(audit.getEntraUserId()).isEqualTo(id);
        assertThat(audit.getStatusChangedBy()).isEqualTo("actor");
        assertThat(audit.getStatusChangedDate()).isNotNull();
        assertThat(audit.getUserEmail()).isEqualTo("j@d.com");
        assertThat(audit.getUserName()).isEqualTo("John Doe");
        assertThat(audit.getDeleteUserReasonEntity()).isSameAs(reason);
        assertThat(audit.getDeletedUserCreatedDate()).isEqualTo(created);
    }

    @Test
    void auditEntityFailsForUnknownReason() {
        when(repository.findByCode(DeleteUserReason.LeftFirm.name())).thenReturn(Optional.empty());
        EntraUser user = EntraUser.builder().build();
        DeleteUserCommand command = new DeleteUserCommand(UUID.randomUUID(), DeleteUserReason.LeftFirm);

        assertThatThrownBy(() -> mapper.toAuditEntity(user, command, "actor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delete reason not found");
    }

    @Test
    void buildsSuccessCommandResult() {
        EntraUser user = EntraUser.builder().entraOid("oid-1").build();

        CommandResult result = mapper.toCommandResult(user);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("User account 'oid-1' deleted successfully");
    }
}
