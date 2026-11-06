package uk.gov.justice.laa.datauserapi.application.query.policy;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountQueryMapper;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.EntraUserQueryRepository;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicy;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationPolicyInputResolver;
import uk.gov.justice.laa.datauserapi.security.rbac.TargetsUser;

@Component
public class TargetUserResolver implements AuthorizationPolicyInputResolver<TargetsUser, EntraUserDto> {

    private final EntraUserQueryRepository entraUserQueryRepository;
    private final UserAccountQueryMapper userAccountQueryMapper;

    public TargetUserResolver(EntraUserQueryRepository entraUserQueryRepository,
                             UserAccountQueryMapper userAccountQueryMapper) {
        this.entraUserQueryRepository = entraUserQueryRepository;
        this.userAccountQueryMapper = userAccountQueryMapper;
    }

    @Override
    public Class<? extends AuthorizationPolicy<EntraUserDto>> policyType() {
        return TargetUserVisibilityPolicy.class;
    }

    @Override
    public Class<TargetsUser> messageType() {
        return TargetsUser.class;
    }

    @Override
    public EntraUserDto resolve(TargetsUser message) {
        String targetOid = message.targetUserEntraObjectId().toString();
        return entraUserQueryRepository.findByEntraOid(targetOid)
                .map(entity -> userAccountQueryMapper.toDto(java.util.Optional.of(entity), targetOid))
                .orElse(null);
    }
}
