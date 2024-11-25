package fi.digitraffic.tis.vaco.ui;

import fi.digitraffic.tis.SpringBootIntegrationTestBase;
import fi.digitraffic.tis.vaco.TestObjects;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.ui.model.Context;
import fi.digitraffic.tis.vaco.ui.model.ImmutableContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ContextServiceIntegrationTests extends SpringBootIntegrationTestBase {

    @Autowired
    private ContextService contextService;
    @Autowired
    private CompanyRepository companyRepository;
    private Company company;

    @BeforeEach
    void setUp() {
        company = TestObjects.aCompany().name(UUID.randomUUID().toString()).build();
        companyRepository.create(company).get();
    }

    @Test
    void testCreateContext() {
        Context context = TestObjects.aContext(company.businessId()).build();
        List<Context> contextList = contextService.create(context);
        assertThat(contextList.size(), equalTo(1));
        Context savedContext = contextList.get(0);
        assertThat(savedContext.context(), equalTo(context.context()));
        assertThat(savedContext.businessId(), equalTo(context.businessId()));
    }

    @Test
    void testUpdateContext() {
        Context context = TestObjects.aContext(company.businessId()).build();
        contextService.create(context);
        Context contextToUpdate = ImmutableContext.of(UUID.randomUUID().toString(), context.businessId());
        List<Context> contextList = contextService.update(context.context(), contextToUpdate);
        assertThat(contextList.size(), equalTo(1));
        Context updatedContext = contextList.get(0);
        assertThat(updatedContext.context(), equalTo(contextToUpdate.context()));
        assertThat(updatedContext.businessId(), equalTo(contextToUpdate.businessId()));
    }

    @Test
    void testListContexts() {
        Context context = TestObjects.aContext(company.businessId()).build();
        contextService.create(context);
        contextService.create(TestObjects.aContext(company.businessId()).build());
        contextService.create(TestObjects.aContext(company.businessId()).build());
        List<Context> contextList = contextService.findByBusinessId(company.businessId());
        assertThat(contextList.size(), equalTo(3));
    }
}
