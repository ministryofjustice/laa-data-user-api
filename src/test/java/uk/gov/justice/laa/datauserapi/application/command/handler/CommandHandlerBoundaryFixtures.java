package uk.gov.justice.laa.datauserapi.application.command.handler;

import uk.gov.justice.laa.datauserapi.application.query.handler.QueryHandlerBoundaryFixtures;

public final class CommandHandlerBoundaryFixtures {

    private CommandHandlerBoundaryFixtures() {
    }

    public static class CommandHandlerFixture {
        private CommandSiblingHandler otherHandler;
        private QueryHandlerBoundaryFixtures.QueryHandler queryHandler;
    }

    public static class CommandSiblingHandler {
    }

    public static class IndependentCommandHandler {
    }
}
