package uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess;

import java.util.UUID;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfilePolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = CanViewProfilePolicy.class)
public record GetUserProfileAppRolesQuery(UUID userProfileId, UUID appId, UUID appEntraClientId)
        implements UserProfileAccessQuery {}
