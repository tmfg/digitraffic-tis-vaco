package fi.digitraffic.tis.vaco;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.configuration.VacoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@link AadOAuth2LoginSecurityConfig#filterChain} rules, which {@link SpringBootIntegrationTestBase}
 * normally switches off. The public NeTEx organisations endpoint is consumed by RAE (Uttu) at startup without
 * credentials, so the boundary between public and private paths must not move by accident.
 */
@Import(PublicEndpointSecurityTests.RealFilterChain.class)
class PublicEndpointSecurityTests extends SpringBootIntegrationTestBase {

    /**
     * The base class disables Azure AD, and with it {@link AadOAuth2LoginSecurityConfig}, through a dynamic property
     * that a subclass cannot override. The filter chain is therefore built here by calling the production method, so
     * the production rules are what is tested.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class RealFilterChain {
        @Bean
        SecurityFilterChain realFilterChain(HttpSecurity http, VacoProperties vacoProperties) throws Exception {
            AadOAuth2LoginSecurityConfig config = new AadOAuth2LoginSecurityConfig();
            return config.filterChain(http, config.corsConfigurationSource(vacoProperties));
        }
    }

    // the real decoder would fetch signing keys from Microsoft; no token is sent in these tests
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private WebApplicationContext context;

    private MockMvc securedMockMvc;

    @BeforeEach
    void setUp_securedMockMvc() {
        securedMockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void publicNetexOrganisationsIsAvailableWithoutAuthorizationHeader() throws Exception {
        securedMockMvc.perform(get("/public/netex/organisations"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
            .andExpect(content().string(containsString("PublicationDelivery")));
    }

    @Test
    void privateNetexOrganisationsRequiresAuthentication() throws Exception {
        securedMockMvc.perform(get("/exports/netex/organisations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void privateNetexAuthoritiesRequiresAuthentication() throws Exception {
        securedMockMvc.perform(get("/exports/netex/authorities"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void otherNonPublicPathRequiresAuthentication() throws Exception {
        securedMockMvc.perform(get("/queue"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publicPrefixDoesNotMatchSimilarlyNamedPath() throws Exception {
        securedMockMvc.perform(get("/publicity/netex/organisations"))
            .andExpect(status().isUnauthorized());
    }
}
