package uk.gov.justice.laa.datauserapi.application.query.queryuserprofiles;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfileListPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = CanViewProfileListPolicy.class)
public record GetUserProfilesQuery(String userEntraObjectId) implements UserProfileQuery {
}
