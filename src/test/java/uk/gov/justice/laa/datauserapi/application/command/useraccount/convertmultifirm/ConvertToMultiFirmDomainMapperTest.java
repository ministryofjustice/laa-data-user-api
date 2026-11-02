package uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertToMultiFirmDomainMapperTest {

    private final ConvertToMultiFirmDomainMapper mapper = new ConvertToMultiFirmDomainMapper();

    @Test
    void toCommandResult_returnsSuccessWithOid() {
        EntraUser user = EntraUser.builder().entraOid("oid-1").build();

        CommandResult result = mapper.toCommandResult(user);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("oid-1");
    }
}
