package uk.gov.justice.laa.datauserapi.application.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.MissingOidException;

@Service
public class UserProfileService {

    public UserProfileDetailView getProfile(AuthenticatedEntraUser authenticatedUser) {
        String oid = authenticatedUser == null ? null : authenticatedUser.entraOid();
        if (oid == null || oid.isBlank()) {
            throw new MissingOidException();
        }

        String email = authenticatedUser.email();
        String name = authenticatedUser.firstName();
        return new UserProfileDetailView(
                UUID.randomUUID(),
                false,
                UserType.EXTERNAL,
                email != null ? email : "",
                name != null ? name : "fullName",
                "firmName",
                "firmId",
                false,
                null,
                null,
                false,
                UUID.fromString(oid),
                false,
                true,
                List.of(),
                List.of()
        );
    }
}
