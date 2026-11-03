package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.EntraUserQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserAccountQueryRepository;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserAccountQueryMapper;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserAccountQueryService {

    private final UserAccountQueryRepository queryRepository;
    private final EntraUserQueryRepository userRepository;
    private final UserAccountQueryMapper mapper;

    public UserAccountQueryService(UserAccountQueryRepository queryRepository,
                                   EntraUserQueryRepository userRepository,
                                   UserAccountQueryMapper mapper) {
        this.queryRepository = queryRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    public List<AccountStatusHistoryView> getAuditHistory(UUID userEntraObjectId) {
        return queryRepository.findAuditHistoryByUserEntraObjectId(String.valueOf(userEntraObjectId));
    }

    public UserAccountStatusView getUserStatus(UUID userEntraObjectId) {
        return userRepository.findStatusByUserEntraObjectId(String.valueOf(userEntraObjectId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEntraObjectId));
    }

    public EntraUserDto findByEntraOid(String entraOid) {
        return mapper.toDto(userRepository.findByEntraOid(entraOid), entraOid);
    }

    public EntraUserDto findUserAccountSummaryByUserEntraObjectId(String userEntraObjectId) {
        return mapper.toDto(userRepository.findByEntraOid(userEntraObjectId), userEntraObjectId);
    }

    public Page<EntraUserDto> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        return mapper.toPage(userRepository.searchUsers(criteria, pageable));
    }
}
