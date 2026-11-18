package uk.gov.justice.laa.datauserapi.pact;

import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;

@Provider("laa-data-user-api")
@PactFolder
class UserDataApiLocalPactTests extends AbstractUserDataApiProviderPactTests {
}
