package uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileDetailsPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = CanViewProfileDetailsPolicy.class)
public record GetActiveProfileQuery(String userEntraObjectId) implements UserProfileQuery {
}
