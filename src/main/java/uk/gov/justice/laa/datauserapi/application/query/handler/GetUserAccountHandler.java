package uk.gov.justice.laa.datauserapi.application.query.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.contracts.response.UserView;
import uk.gov.justice.laa.datauserapi.application.query.mapper.UserViewMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryuserbyid.GetUserAccountQuery;
import uk.gov.justice.laa.datauserapi.application.query.service.UserAccountQueryService;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;
import uk.gov.justice.laa.datauserapi.security.rbac.AuthorizationEntryPoint;

import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class GetUserAccountHandler implements AuthorizationEntryPoint {

    private final UserAccountQueryService queryService;
    private final UserViewMapper userViewMapper;

    public GetUserAccountHandler(
            UserAccountQueryService queryService,
            UserViewMapper userViewMapper) {
        this.queryService = queryService;
        this.userViewMapper = userViewMapper;
    }

    public UserView handle(GetUserAccountQuery query) {
        validateUuidFormat(query.userEntraObjectId(), "userEntraObjectId");
        EntraUserDto target = queryService.findUserAccountSummaryByUserEntraObjectId(
                        query.userEntraObjectId());

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
