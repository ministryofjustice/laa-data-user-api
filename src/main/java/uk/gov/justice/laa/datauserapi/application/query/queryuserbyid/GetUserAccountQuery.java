package uk.gov.justice.laa.datauserapi.application.query.queryuserbyid;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAccountPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = CanViewAccountPolicy.class)
public record GetUserAccountQuery(String userEntraObjectId) {}
