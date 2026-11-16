package uk.gov.justice.laa.datauserapi.application.query.queryusersearch;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanSearchUsersPolicy;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

@PolicyGuarded(policies = CanSearchUsersPolicy.class)
public record SearchUsersQuery(
        UserSearchCriteria criteria
) {}
