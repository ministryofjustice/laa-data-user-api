package uk.gov.justice.laa.datauserapi.controller;

import static net.logstash.logback.argument.StructuredArguments.kv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailResponse;
import uk.gov.justice.laa.datauserapi.security.RequiresReadScope;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiresReadScope
public class ExampleController {

    private static final Logger log = LoggerFactory.getLogger(ExampleController.class);

    public ExampleController() {
    }

    /**
     * Returns the caller's profile derived from the validated JWT {@code oid} claim.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDetailResponse> me(@AuthenticationPrincipal Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        if (oid == null || oid.isBlank()) {
            return ResponseEntity.unprocessableContent().build();
        }
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        log.info("[/me] request",
                kv("oid", oid),
                kv("email", email));

        UserProfileDetailView view = new UserProfileDetailView(
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
        UserProfileDetailResponse response = new UserProfileDetailResponse(view);
        return ResponseEntity.ok(response);
    }
}
