package uk.gov.justice.laa.datauserapi.application.command.service;

import uk.gov.justice.laa.datauserapi.application.command.controller.CommandControllerBoundaryFixtures;
import uk.gov.justice.laa.datauserapi.application.command.handler.CommandHandlerBoundaryFixtures;

public final class CommandServiceBoundaryFixtures {

    private CommandServiceBoundaryFixtures() {
    }

    public static class CommandService {
        private CommandHandlerBoundaryFixtures.CommandHandlerFixture handler;
        private CommandControllerBoundaryFixtures.CommandController controller;
    }
}
