package uk.gov.justice.laa.datauserapi.application.query.queryprofile;

import uk.gov.justice.laa.datauserapi.application.query.policy.ActorProfileValidationPolicy;
import uk.gov.justice.laa.datauserapi.application.query.policy.TargetUserVisibilityPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationEntryPoint;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

import java.util.UUID;

@PolicyGuarded(policies = {ActorProfileValidationPolicy.class, TargetUserVisibilityPolicy.class})
public record GetActiveProfileQuery(
        String userEntraObjectId
) implements AuthorizationEntryPoint, TargetsUser {

    @Override
    public UUID targetUserEntraObjectId() {
        return UUID.fromString(userEntraObjectId);
    }
}
