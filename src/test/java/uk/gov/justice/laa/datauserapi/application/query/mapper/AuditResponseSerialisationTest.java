package uk.gov.justice.laa.datauserapi.application.query.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow;
import uk.gov.justice.laa.datauserapi.config.JacksonConfig;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditView;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The published schema makes these properties optional but not nullable, so absent values must be omitted. */
class AuditResponseSerialisationTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private final AuditQueryMapper mapper = new AuditQueryMapper();

    @Test
    void detailOmitsAbsentLoginDateInviteStatusAndActivationReasons() throws Exception {
        EntraUserDto user = EntraUserDto.builder().entraOid("oid").email("a@b.test").build();
        AuditStatusHistoryRow activation = new AuditStatusHistoryRow("oid", UserAccountAuditStatus.ACTIVATED,
                LocalDateTime.now(), "actor", null, null);

        AuditUserDetailView view = mapper.toDetailView(user, List.of(), 0, List.of(activation));
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(view));

        assertThat(json.get("entraUser").has("lastLoginDate")).isFalse();
        assertThat(json.get("entraUser").has("entraInviteStatus")).isFalse();
        JsonNode history = json.get("accountStatusHistory").get(0);
        assertThat(history.get("statusChange").asText()).isEqualTo("ACTIVATED");
        assertThat(history.has("deactivateReason")).isFalse();
        assertThat(history.has("deactivateReasonLabel")).isFalse();
    }

    @Test
    void deletedUserOmitsAbsentNameAndReason() throws Exception {
        DeletedUserAuditView view = new DeletedUserAuditView("oid", null, "a@b.test", "actor", LocalDateTime.now(),
                null, "Left firm");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(view));

        assertThat(json.has("name")).isFalse();
        assertThat(json.has("deleteReason")).isFalse();
        assertThat(json.get("deleteReasonLabel").asText()).isEqualTo("Left firm");
    }
}
