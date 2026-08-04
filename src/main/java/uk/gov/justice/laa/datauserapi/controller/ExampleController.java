package uk.gov.justice.laa.datauserapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ExampleController {

    /**
     * Returns the caller's identity derived from the validated JWT {@code oid} claim.
     * No Graph lookup — actor is identified locally from the JWT only.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(@AuthenticationPrincipal Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        String subject = jwt.getSubject();
        return ResponseEntity.ok(Map.of("oid", oid != null ? oid : "", "sub", subject != null ? subject : ""));
    }
}
