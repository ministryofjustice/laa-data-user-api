package uk.gov.justice.laa.datauserapi.application.query.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserAccountDetailsService {

    private final UserAccountQueryService queryService;
    private final UserViewMapper userViewMapper;

    public UserAccountDetailsService(
            UserAccountQueryService queryService,
            UserViewMapper userViewMapper) {
        this.queryService = queryService;
        this.userViewMapper = userViewMapper;
    }

    public UserView getAccount(String userEntraObjectId, String actorOid) {
        validateUuidFormat(userEntraObjectId, "userEntraObjectId");
        EntraUserDto target = queryService.findUserAccountSummaryByUserEntraObjectId(userEntraObjectId);

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