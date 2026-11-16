package uk.gov.justice.laa.datauserapi.application.query.handler;

import uk.gov.justice.laa.datauserapi.application.command.handler.CommandHandlerBoundaryFixtures;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.QueryRepositoryBoundaryFixtures;

public final class QueryHandlerBoundaryFixtures {

    private QueryHandlerBoundaryFixtures() {
    }

    public static class QueryHandler {
        private QuerySiblingHandler otherHandler;
        private QueryRepositoryBoundaryFixtures.QueryRepositoryFixture repository;
        private CommandHandlerBoundaryFixtures.CommandHandlerFixture commandHandler;
    }

    public static class QuerySiblingHandler {
    }

    public static class IndependentQueryHandler {
    }
}
