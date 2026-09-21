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
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
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
        UserProfileDetailResponse response = new UserProfileDetailResponse(
                UUID.randomUUID(),
                oid,
                "EXTERNAL",
                email != null ? email : "",
                name != null ? name : "",
                null,
                null,
                "ACTIVE",
                "ACTIVE",
                false,
                false,
                false
        );
        return ResponseEntity.ok(response);
    }
}
