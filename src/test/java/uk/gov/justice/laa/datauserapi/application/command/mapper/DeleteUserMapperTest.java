package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.request.DeleteUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.DeleteUserReasonEntity;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteUserMapperTest {

    private final DeleteUserMapper mapper = new DeleteUserMapper();

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
        EntraUser user = EntraUser.builder().entraOid("oid-1").firstName("John").lastName("Doe").email("j@d.com")
                .userProfiles(Set.of(UserProfile.builder().userType(UserType.EXTERNAL).build())).build();
        user.setId(id);
        user.setCreatedDate(created);
        var reason = DeleteUserReasonEntity.builder().code("LeftFirm").build();

        UserAccountStatusAudit audit = mapper.toAuditEntity(user,
                reason, "actor");

        assertThat(audit.getUserAccountAuditStatus()).isEqualTo(UserAccountAuditStatus.DELETED);
        assertThat(audit.getEntraUserId()).isEqualTo(id);
        assertThat(audit.getStatusChangedBy()).isEqualTo("actor");
        assertThat(audit.getStatusChangedDate()).isNotNull();
        assertThat(audit.getUserEmail()).isEqualTo("j@d.com");
        assertThat(audit.getUserName()).isEqualTo("John Doe");
        assertThat(audit.getDeleteUserReasonEntity()).isSameAs(reason);
        assertThat(audit.getDeletedUserCreatedDate()).isEqualTo(created);
        assertThat(audit.getDeletedUserEntraOid()).isEqualTo("oid-1");
        assertThat(audit.getDeletedUserType()).isEqualTo(UserType.EXTERNAL);
    }

    @Test
    void recordsInternalUserTypeWhenTheUserHasAnInternalProfile() {
        UserProfile internalProfile = UserProfile.builder().userType(UserType.INTERNAL).build();
        EntraUser user = EntraUser.builder().firstName("John").lastName("Doe").email("j@d.com")
                .userProfiles(Set.of(internalProfile)).build();

        UserAccountStatusAudit audit = mapper.toAuditEntity(user, DeleteUserReasonEntity.builder().code("LeftFirm").build(),
                "actor");

        assertThat(audit.getDeletedUserType()).isEqualTo(UserType.INTERNAL);
    }

    @Test
    void classifiesAnAccountWithoutProfilesByItsMultiFirmFlag() {
        var reason = DeleteUserReasonEntity.builder().code("LeftFirm").build();
        EntraUser single = EntraUser.builder().firstName("A").lastName("B").email("a@b.com").build();
        EntraUser multi = EntraUser.builder().firstName("A").lastName("B").email("a@b.com")
                .multiFirmUser(true).userProfiles(Set.of()).build();

        assertThat(mapper.toAuditEntity(single, reason, "actor").getDeletedUserType()).isEqualTo(UserType.INTERNAL);
        assertThat(mapper.toAuditEntity(multi, reason, "actor").getDeletedUserType()).isEqualTo(UserType.EXTERNAL);
    }

    @Test
    void buildsSuccessCommandResult() {
        EntraUser user = EntraUser.builder().entraOid("oid-1").build();

        CommandResult result = mapper.toCommandResult(user);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("User account 'oid-1' deleted successfully");
    }
}
