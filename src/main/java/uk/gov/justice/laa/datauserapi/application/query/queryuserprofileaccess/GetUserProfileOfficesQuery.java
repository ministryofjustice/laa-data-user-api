package uk.gov.justice.laa.datauserapi.application.query.queryuserprofileaccess;

import java.util.UUID;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewProfilePolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = CanViewProfilePolicy.class)
public record GetUserProfileOfficesQuery(UUID userProfileId) implements UserProfileAccessQuery {}
