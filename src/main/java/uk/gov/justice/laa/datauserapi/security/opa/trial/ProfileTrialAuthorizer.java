package uk.gov.justice.laa.datauserapi.security.opa.trial;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.time.Clock;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.security.opa.OpaAuthorizationClient;

@Component
@Slf4j
public class ProfileTrialAuthorizer {
    private final JavaProfileTrialRules javaRules;
    private final OpaAuthorizationClient client;
    private final ProfileTrialConfiguration.Engines engines;
    private final Clock clock;

    public ProfileTrialAuthorizer(JavaProfileTrialRules javaRules, OpaAuthorizationClient client,
                                  ProfileTrialConfiguration.Engines engines, Clock profileTrialClock) {
        this.javaRules = javaRules;
        this.client = client;
        this.engines = engines;
        this.clock = profileTrialClock;
    }

    public ProfileTrialDecision decide(ProfileTrialInput input) {
        long start = clock.millis();
        ProfileTrialDecision legacy = javaRules.evaluate(input);
        ProfileTrialDecision opa = engines.opaEnabled() ? client.profileDecision(input) : null;
        boolean useLegacy = engines.legacyPoliciesEnabled() || !engines.opaEnabled();
        ProfileTrialDecision result = opa == null ? legacy : opa;
        if (useLegacy && !legacy.allow()) {
            result = legacy;
        } else if (useLegacy && opa != null && opa.allow()) {
            var types = new HashSet<>(opa.queryFilters().userTypes());
            types.retainAll(legacy.queryFilters().userTypes());
            var roles = new HashSet<>(opa.queryFilters().roleIds());
            roles.retainAll(legacy.queryFilters().roleIds());
            var firm = opa.queryFilters().firmId() == null ? legacy.queryFilters().firmId() : opa.queryFilters().firmId();
            if (opa.queryFilters().firmId() != null && legacy.queryFilters().firmId() != null
                    && !opa.queryFilters().firmId().equals(legacy.queryFilters().firmId())) {
                result = ProfileTrialDecision.deny(ProfileTrialDecision.Reason.FIRM_SCOPE);
            } else {
                result = new ProfileTrialDecision(true, ProfileTrialDecision.Reason.ALLOWED,
                        new ProfileTrialDecision.Filters(firm, types, roles));
            }
        }
        boolean mismatch = opa != null && !opa.equals(legacy);
        var decisionLog = result.allow() && !mismatch ? log.atInfo() : log.atWarn();
        decisionLog.log("Profile trial authorisation evaluated",
                kv("event", "opa_trial_decision"), kv("operation", input.operation()),
                kv("actorId", input.actor().id()), kv("profileId", input.target() == null ? null : input.target().id()),
                kv("allowed", result.allow()), kv("reasonCode", result.reason()),
                kv("javaAllowed", legacy.allow()), kv("opaAllowed", opa == null ? null : opa.allow()),
                kv("javaReason", legacy.reason()), kv("opaReason", opa == null ? null : opa.reason()),
                kv("javaFilters", legacy.queryFilters()), kv("opaFilters", opa == null ? null : opa.queryFilters()),
                kv("engineMismatch", mismatch), kv("durationMs", clock.millis() - start));
        return result;
    }
}
