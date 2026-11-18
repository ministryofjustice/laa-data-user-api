package uk.gov.justice.laa.datauserapi.application.command.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.model.CcmsMessage;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.RoleChangeOutboxRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.entity.RoleChangeOutboxEvent;
import uk.gov.justice.laa.datauserapi.application.command.dto.RoleNotificationDetails;
import uk.gov.justice.laa.datauserapi.exception.CcmsNotificationException;
import uk.gov.justice.laa.datauserapi.security.opa.trial.ProfileTrialInput;

@Component
public class RoleChangeOutboxWriter {
    private final RoleChangeOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RoleChangeOutboxWriter(RoleChangeOutboxRepository repository, ObjectMapper objectMapper, Clock profileTrialClock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = profileTrialClock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(RoleNotificationDetails profile, ProfileTrialInput input, Set<UUID> finalRoles) {
        Map<String, Set<String>> before = ccmsRoles(input, input.target().roleIds());
        Map<String, Set<String>> after = ccmsRoles(input, finalRoles);
        Set<String> applications = new java.util.HashSet<>(before.keySet());
        applications.addAll(after.keySet());
        var now = clock.instant();
        boolean queued = false;
        for (String application : applications) {
            if (before.getOrDefault(application, Set.of()).equals(after.getOrDefault(application, Set.of()))) {
                continue;
            }
            if (profile.legacyUserId() == null || (profile.userType() == UserType.EXTERNAL && profile.firmCode() == null)) {
                throw new CcmsNotificationException(CcmsNotificationException.Reason.INVALID_PROFILE,
                        "A CCMS profile has no downstream identifier or firm code", null);
            }
            CcmsMessage payload = CcmsMessage.builder().userName(profile.legacyUserId().toString())
                    .vendorNumber(profile.userType() == UserType.EXTERNAL ? profile.firmCode() : null)
                    .firstName(profile.firstName()).lastName(profile.lastName()).email(profile.email())
                    .timestamp(LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                    .responsibilityKey(after.getOrDefault(application, Set.of()).stream().sorted().toList()).build();
            String json;
            try {
                json = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException exception) {
                throw new CcmsNotificationException("CCMS outbox serialization failed", exception);
            }
            RoleChangeOutboxEvent event = new RoleChangeOutboxEvent();
            event.setEventId(UUID.randomUUID());
            event.setProfileId(profile.profileId());
            event.setApplicationOid(application);
            event.setGroupId(profile.legacyUserId().toString());
            event.setUserType(profile.userType().name());
            event.setPayload(json);
            event.setCreatedAt(now);
            event.setNextAttemptAt(now);
            repository.save(event);
            queued = true;
        }
        return queued;
    }

    private static Map<String, Set<String>> ccmsRoles(ProfileTrialInput input, Set<UUID> ids) {
        List<ProfileTrialInput.Role> roles = ids.stream().map(input.roles()::get)
                .filter(ProfileTrialInput.Role::legacySync).toList();
        if (roles.stream().anyMatch(role -> role.appEntraOid() == null || role.appEntraOid().isBlank()
                || role.identifier() == null || role.identifier().isBlank())) {
            throw new CcmsNotificationException(CcmsNotificationException.Reason.INVALID_PROFILE,
                    "A CCMS role application has no downstream identifier", null);
        }
        return roles.stream().collect(Collectors.groupingBy(ProfileTrialInput.Role::appEntraOid,
                Collectors.mapping(ProfileTrialInput.Role::identifier, Collectors.toSet())));
    }
}
