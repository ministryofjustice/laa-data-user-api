package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountVisibilityContext;
import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAccountPolicy;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Loads, with a single read, the trusted target-user facts that {@link CanViewAccountPolicy} evaluates. */
@Component
@Transactional(readOnly = true)
public class UserAccountVisibilityPolicyInputResolver
        implements AuthorizationPolicyInputResolver<GetUserAccountQuery, UserAccountVisibilityContext> {

    private final UserAccountQueryService queryService;

    public UserAccountVisibilityPolicyInputResolver(UserAccountQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public Class<? extends AuthorizationPolicy<UserAccountVisibilityContext>> policyType() {
        return CanViewAccountPolicy.class;
    }

    @Override
    public Class<GetUserAccountQuery> messageType() {
        return GetUserAccountQuery.class;
    }

    @Override
    public UserAccountVisibilityContext resolve(GetUserAccountQuery message) {
        EntraUserDto user = queryService.findUserAccountSummaryByUserEntraObjectId(message.userEntraObjectId());
        
        if (user == null) {
            return UserAccountVisibilityContext.missing();
        }
        
        boolean hasInternalProfile = user.getUserProfiles().stream()
                .anyMatch(profile -> profile.getUserType().toString().equals("INTERNAL"));
        boolean hasExternalProfile = user.getUserProfiles().stream()
                .anyMatch(profile -> profile.getUserType().toString().equals("EXTERNAL"));
        
        // If account has no profiles, treat as EXTERNAL if multiFirmUser, else INTERNAL
        if (user.getUserProfiles().isEmpty()) {
            boolean isMultiFirmUser = user.isMultiFirmUser();
            hasExternalProfile = isMultiFirmUser;
            hasInternalProfile = !isMultiFirmUser;
        }
        
        // Collect ALL firm IDs from external profiles
        Set<UUID> externalFirmIds = user.getUserProfiles().stream()
                .filter(profile -> profile.getUserType().toString().equals("EXTERNAL"))
                .map(profile -> profile.getFirm() != null ? profile.getFirm().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        return new UserAccountVisibilityContext(true, hasInternalProfile, hasExternalProfile, externalFirmIds, user.getEntraOid());
    }
}

