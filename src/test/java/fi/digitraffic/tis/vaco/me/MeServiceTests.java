package fi.digitraffic.tis.vaco.me;

import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.Company;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class MeServiceTests extends SpringBootIntegrationTestBase {

    @Autowired
    private MeService meService;

    @Test
    void resolvingUserWithoutAnyGroups() {
        JwtAuthenticationToken token = TestObjects.jwtNoGroupsToken("Jornado");
        SecurityContextHolder.getContext().setAuthentication(token);

        List<Company> companies = meService.findCompanies().stream().toList();
        assertThat(companies.size(), equalTo(1));
        Company publicTest = companies.get(0);
        assertThat(publicTest.businessId(), equalTo(Constants.PUBLIC_VALIDATION_TEST_ID));
    }
}
