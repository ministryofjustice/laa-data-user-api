package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.query.dto.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.application.query.dto.UserAccountStatusView;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.UserAccountQueryRepository;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserAccountQueryService {

    private final UserAccountQueryRepository queryRepository;
    private final EntraUserCommandRepository userRepository;

    public UserAccountQueryService(UserAccountQueryRepository queryRepository,
                                   EntraUserCommandRepository userRepository) {
        this.queryRepository = queryRepository;
        this.userRepository = userRepository;
    }

    public List<AccountStatusHistoryView> getAuditHistory(UUID userEntraObjectId) {
        return queryRepository.findAuditHistoryByUserEntraObjectId(userEntraObjectId);
    }

    public UserAccountStatusView getUserStatus(UUID userEntraObjectId) {
        EntraUser user = userRepository.findById(userEntraObjectId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEntraObjectId));
        return new UserAccountStatusView(
                user.getEntraOid(),
                user.getEmail(),
                user.getUserAccountStatus(),
                user.getLastModified()
        );
    }
}
