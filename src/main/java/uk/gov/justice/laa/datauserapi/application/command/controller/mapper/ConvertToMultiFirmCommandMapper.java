package uk.gov.justice.laa.datauserapi.application.command.controller.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.ConvertToMultiFirmRequest;

@Component
public class ConvertToMultiFirmCommandMapper {

    public ConvertToMultiFirmCommand toCommand(ConvertToMultiFirmRequest request) {
        return new ConvertToMultiFirmCommand(request.userEntraObjectId());
    }
}
