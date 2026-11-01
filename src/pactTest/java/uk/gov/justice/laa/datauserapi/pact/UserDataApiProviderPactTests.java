package uk.gov.justice.laa.datauserapi.pact;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;

@Provider("laa-data-user-api")
@PactBroker
public class UserDataApiProviderPactTests extends AbstractProviderPactTests {
}
