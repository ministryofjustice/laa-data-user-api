package uk.gov.justice.laa.datauserapi.service;

import uk.gov.justice.laa.datauserapi.command.CreateUserAccountCommand;

public interface EntraUserClient {
    String createUser(CreateUserAccountCommand command);
}
