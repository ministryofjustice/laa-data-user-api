package uk.gov.justice.laa.datauserapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TEMPORARY — for manually verifying Sentry capture on a deployed environment. Remove after use.
@RestController
@RequestMapping("/api/v1")
public class SentryTestController {

    @GetMapping("/sentry-test")
    public void triggerError() {
        throw new RuntimeException("Sentry test event - safe to ignore");
    }
}
