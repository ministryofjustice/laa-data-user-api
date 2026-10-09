package uk.gov.justice.laa.datauserapi.application.query.service;

import org.modelmapper.ModelMapper;
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
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserAccountQueryService {

    private final UserAccountQueryRepository queryRepository;
    private final EntraUserQueryRepository userRepository;
    private final ModelMapper modelMapper;

    public UserAccountQueryService(UserAccountQueryRepository queryRepository,
                                   EntraUserQueryRepository userRepository,
                                   ModelMapper modelMapper) {
        this.queryRepository = queryRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
    }

    public List<AccountStatusHistoryView> getAuditHistory(UUID userEntraObjectId) {
        return queryRepository.findAuditHistoryByUserEntraObjectId(userEntraObjectId);
    }

    public UserAccountStatusView getUserStatus(UUID userEntraObjectId) {
        return userRepository.findStatusByUserEntraObjectId(userEntraObjectId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEntraObjectId));
    }

    public EntraUserDto findByEntraOid(String entraOid) {
        EntraUser user = userRepository.findByEntraOid(entraOid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + entraOid));
        return modelMapper.map(user, EntraUserDto.class);
    }

    public EntraUserDto findUserAccountSummaryByUserEntraObjectId(String userEntraObjectId) {
        EntraUser user = userRepository.findByEntraOid(userEntraObjectId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEntraObjectId));
        return modelMapper.map(user, EntraUserDto.class);
    }

    public Page<EntraUserDto> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        return userRepository.searchUsers(criteria, pageable).map(user -> modelMapper.map(user, EntraUserDto.class));
    }
}
