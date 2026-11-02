package uk.gov.justice.laa.datauserapi.application.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;

@Slf4j
@Service
public class UserProfileService {

    public Optional<UserProfileDetailView> getProfile(AuthenticatedEntraUser authenticatedUser) {
        String oid = authenticatedUser == null ? null : authenticatedUser.entraOid();
        if (oid == null || oid.isBlank()) {
            log.warn("Cannot build caller profile without an Entra OID", kv("entraOid", oid));
            return Optional.empty();
        }

        log.info("Building caller profile", kv("entraOid", oid));
        String email = authenticatedUser.email();
        String name = authenticatedUser.firstName();
        return Optional.of(new UserProfileDetailView(
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
        ));
    }
}
