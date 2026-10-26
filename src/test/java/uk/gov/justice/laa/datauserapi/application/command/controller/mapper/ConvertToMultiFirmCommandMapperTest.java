package uk.gov.justice.laa.datauserapi.application.command.controller.mapper;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.ConvertToMultiFirmRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConvertToMultiFirmCommandMapperTest {

    @Test
    void toCommand_mapsUserId() {
        UUID id = UUID.randomUUID();

        ConvertToMultiFirmCommand command =
                new ConvertToMultiFirmCommandMapper().toCommand(new ConvertToMultiFirmRequest(id));

        assertThat(command.userEntraObjectId()).isEqualTo(id);
    }
}
