package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

public record AuditUserDetailView(
        AuditEntraUserView entraUser,
        List<UserProfileDetailView> profiles,
        List<AccountStatusHistoryView> accountStatusHistory
) {}
