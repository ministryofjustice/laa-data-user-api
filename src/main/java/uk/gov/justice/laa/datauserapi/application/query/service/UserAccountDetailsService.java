package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;
import uk.gov.justice.laa.datauserapi.exception.ForbiddenResponseConstants;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserAccountDetailsService {

    private final UserAccountQueryService queryService;
    private final UserViewMapper userViewMapper;
    private final QueryAuthorisationService authorisationService;

    public UserAccountDetailsService(
            UserAccountQueryService queryService,
            UserViewMapper userViewMapper, QueryAuthorisationService authorisationService) {
        this.queryService = queryService;
        this.userViewMapper = userViewMapper;
        this.authorisationService = authorisationService;
    }

    public UserView getAccount(GetUserAccountQuery query) {
        EntraUserDto actor = queryService.findByEntraOid(query.actorOid());

        UserProfileDto actorProfile = authorisationService.requireActiveProfile(actor, query.actorOid());

        validateUuidFormat(query.userEntraObjectId(), "userEntraObjectId");
        EntraUserDto target = queryService.findUserAccountSummaryByUserEntraObjectId(
                        query.userEntraObjectId());

        if (!authorisationService.canViewAccount(actorProfile, target)) {
            throw new AccessDeniedException(ForbiddenResponseConstants.ACCESS_DENIED_MESSAGE);
        }

        return userViewMapper.mapToUserView(target);
    }

    private void validateUuidFormat(String value, String fieldName) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidUuidFormatException(
                    String.format("%s must match UUID format", fieldName));
        }
    }
}
