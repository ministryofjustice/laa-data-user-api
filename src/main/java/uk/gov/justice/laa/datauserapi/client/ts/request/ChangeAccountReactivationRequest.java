package uk.gov.justice.laa.datauserapi.client.ts.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeAccountReactivationRequest {

    AccountReactivationdBody accountReactivationdBody;

    public ChangeAccountReactivationRequest(boolean active, String deactivateUser) {
        this.accountReactivationdBody = new AccountReactivationdBody(active, deactivateUser);
    }

    public ChangeAccountReactivationRequest(boolean active) {
        this.accountReactivationdBody = new AccountReactivationdBody(active, null);
    }

    @Getter
    @Setter
    @AllArgsConstructor
    static class AccountReactivationdBody {
        private boolean active;
        private String deactivationReason;
    }
}
