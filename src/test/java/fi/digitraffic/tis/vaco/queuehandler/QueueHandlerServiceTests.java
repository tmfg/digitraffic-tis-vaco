package fi.digitraffic.tis.vaco.queuehandler;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fi.digitraffic.tis.Constants;
import fi.digitraffic.tis.vaco.TestConstants;
import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.model.ImmutableCompany;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.service.CompanyHierarchyService;
import fi.digitraffic.tis.vaco.entries.EntryService;
import fi.digitraffic.tis.vaco.me.MeService;
import fi.digitraffic.tis.vaco.messaging.MessagingService;
import fi.digitraffic.tis.vaco.messaging.model.DelegationJobMessage;
import fi.digitraffic.tis.vaco.queuehandler.model.Entry;
import fi.digitraffic.tis.vaco.queuehandler.model.ImmutableEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Function;

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

    @Mock
    private CachingService cachingService;

    @Mock
    private MeService meService;

    @Mock
    private EntryService entryService;

    @Mock
    private MessagingService messagingService;

    @Mock
    private CompanyHierarchyService companyHierarchyService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Captor
    private ArgumentCaptor<DelegationJobMessage> delegationJobCaptor;

    @Captor
    private ArgumentCaptor<ImmutableCompany> companyCaptor;

    private String operatorBusinessId;
    private String operatorName;
    private ObjectNode metadata;
    private ImmutableEntry entryRequest;
    private ImmutableCompany fintrafficCompany;

    @BeforeEach
    void setUp() {
        // simple dependencies don't need to be mocked
        objectMapper = new ObjectMapper();

        queueHandlerService = new QueueHandlerService(
            cachingService,
            meService,
            entryService,
            messagingService,
            companyHierarchyService,
            transactionTemplate);

        operatorBusinessId = "123-4";
        operatorName = "Oppypop Oy";

        metadata = objectMapper.getNodeFactory().objectNode()
            .put("caller", "FINAP")
            .put("operator-name", operatorName);

        entryRequest = ImmutableEntry.builder()
            .publicId(NanoIdUtils.randomNanoId())
            .name("fake gtfs entry")
            .businessId(operatorBusinessId)
            .url(TestConstants.EXAMPLE_URL + "/gtfs.zip")
            .format("gtfs")
            .metadata(metadata)
            .build();

        fintrafficCompany = ImmutableCompany.of(Constants.FINTRAFFIC_BUSINESS_ID, TestConstants.FINTRAFFIC_COMPANY_NAME);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(
            cachingService,
            meService,
            entryService,
            messagingService,
            companyHierarchyService,
            transactionTemplate);
    }

    private <T> Answer<T> withArg(int i) {
        return a -> a.getArgument(i);
    }

    private <T> Answer<Optional<T>> withArgInOptional(int i) {
        return a -> Optional.ofNullable(a.getArgument(i));
    }

    @Test
    void autocreatesCompanyOnNewEntryIfSourceIsFinap() {
        // given
        givenTransactionRunsSuccessfully();
        given(entryService.create(any(Entry.class))).willAnswer(withArg(0));
        given(companyHierarchyService.createCompany(any(ImmutableCompany.class))).willAnswer(withArgInOptional(0));
        given(companyHierarchyService.findByBusinessId(Constants.FINTRAFFIC_BUSINESS_ID)).willReturn(Optional.of(fintrafficCompany));
        givenCachesResult();

        // when
        Entry result = queueHandlerService.processQueueEntry(entryRequest);

        // then
        then(companyHierarchyService).should().createCompany(companyCaptor.capture());
        ImmutableCompany operator = companyCaptor.getValue();
        assertThat(operator.businessId(), equalTo(operatorBusinessId));
        assertThat(operator.name(), equalTo(operatorName));

        then(companyHierarchyService).should().createPartnership(eq(PartnershipType.AUTHORITY_PROVIDER), eq(fintrafficCompany), eq(operator));

        thenSubmitProcessingJob(result);

        //thenCacheEntry(result, true);
    }

    @Test
    void wontAutocreateCompanyIfCallerIsNotFinap() {
        // given
        givenTransactionRunsSuccessfully();
        given(entryService.create(any(Entry.class))).willAnswer(withArg(0));
        givenCachesResult();

        // when
        entryRequest = entryRequest.withMetadata(metadata.put("caller", "Graham Bell"));
        Entry result = queueHandlerService.processQueueEntry(entryRequest);

        // then
        thenSubmitProcessingJob(result);
    }

    @Test
    void wontAutocreateCompanyIfOperatorNameIsMissing() {
        // given
        givenTransactionRunsSuccessfully();
        given(entryService.create(any(Entry.class))).willAnswer(withArg(0));
        givenCachesResult();

        // when
        entryRequest = entryRequest.withMetadata(metadata.remove("operator-name"));
        Entry result = queueHandlerService.processQueueEntry(entryRequest);

        // then
        thenSubmitProcessingJob(result);
    }

    private void givenTransactionRunsSuccessfully() {
        given(transactionTemplate.execute(any(TransactionCallback.class))).will(a -> {
            return ((TransactionCallback<Entry>) a.getArgument(0)).doInTransaction(new TransactionStatus() {
                @Override
                public Object createSavepoint() throws TransactionException {
                    return null;
                }

                @Override
                public void rollbackToSavepoint(Object savepoint) throws TransactionException {

                }

                @Override
                public void releaseSavepoint(Object savepoint) throws TransactionException {

                }
            });
        });
    }

    private void thenSubmitProcessingJob(Entry result) {
        then(messagingService).should().submitProcessingJob(delegationJobCaptor.capture());
        DelegationJobMessage job = delegationJobCaptor.getValue();
        assertThat(job, notNullValue());
        assertThat(job.entry(), equalTo(result));
    }

    private void givenCachesResult() {
        given(cachingService.cacheEntry(any(String.class), any(Function.class)))
            .willAnswer(a ->
                Optional.ofNullable(((Function) a.getArgument(1)).apply(a.getArgument(0))));
    }
}
