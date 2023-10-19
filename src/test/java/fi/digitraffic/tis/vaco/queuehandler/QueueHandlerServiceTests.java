package fi.digitraffic.tis.vaco.queuehandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.organization.model.CooperationType;
import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.service.CooperationService;
import fi.digitraffic.tis.vaco.organization.service.OrganizationService;
import fi.digitraffic.tis.vaco.queuehandler.dto.ImmutableEntryRequest;
import fi.digitraffic.tis.vaco.queuehandler.mapper.EntryRequestMapper;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.repository.QueueHandlerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class QueueHandlerServiceTests {

    private QueueHandlerService queueHandlerService;

    private ObjectMapper objectMapper;
    private EntryRequestMapper entryRequestMapper;

    @Mock
    private MessagingService messagingService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private QueueHandlerRepository queueHandlerRepository;

    @Mock
    private CooperationService cooperationService;

    @Captor
    private ArgumentCaptor<DelegationJobMessage> delegationJobCaptor;

    @Captor
    private ArgumentCaptor<ImmutableOrganization> organizationCaptor;

    private String operatorBusinessId;
    private String operatorName;
    private ObjectNode metadata;
    private ImmutableEntryRequest entryRequest;
    private ImmutableOrganization fintrafficOrg;

    @BeforeEach
    void setUp() {
        // simple dependencies don't need to be mocked
        objectMapper = new ObjectMapper();
        entryRequestMapper = new EntryRequestMapper(objectMapper);

        queueHandlerService = new QueueHandlerService(
            entryRequestMapper,
            messagingService,
            organizationService,
            queueHandlerRepository,
            cooperationService);

        operatorBusinessId = "123-4";
        operatorName = "Oppypop Oy";

        metadata = objectMapper.getNodeFactory().objectNode()
            .put("caller", "FINAP")
            .put("operator-name", operatorName);

        entryRequest = ImmutableEntryRequest.builder()
            .businessId(operatorBusinessId)
            .url(TestConstants.EXAMPLE_URL + "/gtfs.zip")
            .format("gtfs")
            .metadata(metadata)
            .build();

        fintrafficOrg = ImmutableOrganization.of(Constants.FINTRAFFIC_BUSINESS_ID, TestConstants.FINTRAFFIC_ORGANIZATION_NAME);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
            messagingService,
            organizationService,
            queueHandlerRepository,
            cooperationService);
    }

    private <T> Answer<T> withArg(int i) {
        return a -> a.getArgument(i);
    }

    private <T> Answer<Optional<T>> withArgInOptional(int i) {
        return a -> Optional.ofNullable(a.getArgument(i));
    }

    @Test
    void autocreatesOrganizationOnNewEntryIfSourceIsFinap() {
        // given
        given(queueHandlerRepository.create(any(Entry.class))).willAnswer(withArg(0));
        given(organizationService.createOrganization(any(ImmutableOrganization.class))).willAnswer(withArgInOptional(0));
        given(organizationService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID)).willReturn(Optional.of(fintrafficOrg));

        // when
        Entry result = queueHandlerService.processQueueEntry(entryRequest);

        // then
        then(organizationService).should().createOrganization(organizationCaptor.capture());
        ImmutableOrganization operator = organizationCaptor.getValue();
        assertThat(operator.businessId(), equalTo(operatorBusinessId));
        assertThat(operator.name(), equalTo(operatorName));

        then(cooperationService).should().create(eq(CooperationType.AUTHORITY_PROVIDER), eq(fintrafficOrg), eq(operator));

        thenSubmitProcessingJob(result);
    }

    @Test
    void wontAutocreateOrganizationIfCallerIsNotFinap() {
        // given
        given(queueHandlerRepository.create(any(Entry.class))).willAnswer(withArg(0));

        // when
        entryRequest = entryRequest.withMetadata(metadata.put("caller", "Graham Bell"));
        Entry result = queueHandlerService.processQueueEntry(entryRequest);

        // then
        thenSubmitProcessingJob(result);
    }

    @Test
    void wontAutocreateOrganizationIfOperatorNameIsMissing() {
        // given
        given(queueHandlerRepository.create(any(Entry.class))).willAnswer(withArg(0));

        // when
        entryRequest = entryRequest.withMetadata(metadata.remove("operator-name"));
        Entry result = queueHandlerService.processQueueEntry(entryRequest);

        // then
        thenSubmitProcessingJob(result);
    }

    private void thenSubmitProcessingJob(Entry result) {
        then(messagingService).should().submitProcessingJob(delegationJobCaptor.capture());
        DelegationJobMessage job = delegationJobCaptor.getValue();
        assertThat(job, notNullValue());
        assertThat(job.entry(), equalTo(result));
    }
}
