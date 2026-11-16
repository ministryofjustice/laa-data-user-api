package uk.gov.justice.laa.datauserapi.security.rbac;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyDecisionTest {

    @Test
    void deniedDecisionsRequireStableReasonCode() {
        assertThat(PolicyDecision.deny(PolicyDenialReason.SELF_TARGET_NOT_ALLOWED).reason())
                .isEqualTo(PolicyDenialReason.SELF_TARGET_NOT_ALLOWED);
        assertThat(PolicyDecision.allow().reason()).isNull();
        assertThatThrownBy(() -> PolicyDecision.deny(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PolicyDecision(true, PolicyDenialReason.POLICY_DENIED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
