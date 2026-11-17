package uk.gov.justice.laa.datauserapi.application.query.queryuserbyid;

import uk.gov.justice.laa.datauserapi.application.query.policy.CanViewAccountPolicy;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyGuarded;

import java.util.UUID;

@PolicyGuarded(policies = CanViewAccountPolicy.class)
public record GetUserAccountQuery(String userEntraObjectId) {

    public GetUserAccountQuery {
        validateUuidFormat(userEntraObjectId);
    }

    private static void validateUuidFormat(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidUuidFormatException(
                    String.format("userEntraObjectId must match UUID format"));
        }
    }
}
