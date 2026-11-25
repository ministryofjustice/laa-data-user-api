package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanExportAuditUsersPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;

import static org.assertj.core.api.Assertions.assertThat;

class AuditUserExportAuthorizationPolicyInputResolverTest {

    private final AuditUserExportAuthorizationPolicyInputResolver resolver =
            new AuditUserExportAuthorizationPolicyInputResolver();

    @Test
    void resolve_extractsRequestedUserTypeAndFirmIdFromCriteria() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.EXTERNAL, null, "FIRM001", null, null, null, null);

        var context = resolver.resolve(new ExportAuditUsersQuery(criteria));

        assertThat(context.requestedUserType()).isEqualTo(UserType.EXTERNAL);
        assertThat(context.requestedFirmId()).isEqualTo("FIRM001");
        assertThat(context.requestsInternalOnly()).isFalse();
    }

    @Test
    void resolve_internalOnlyRequest_hasNoRequestedFirmId() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);

        var context = resolver.resolve(new ExportAuditUsersQuery(criteria));

        assertThat(context.requestsInternalOnly()).isTrue();
        assertThat(context.requestedFirmId()).isNull();
    }

    @Test
    void policyType_and_messageType_matchDeclaredPolicy() {
        assertThat(resolver.policyType()).isEqualTo(CanExportAuditUsersPolicy.class);
        assertThat(resolver.messageType()).isEqualTo(ExportAuditUsersQuery.class);
    }
}
