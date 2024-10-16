package fi.digitraffic.tis.vaco.exports;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.exports.utils.NetexObjectFactory;
import fi.digitraffic.tis.vaco.fintrafficid.FintrafficIdService;
import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;
import jakarta.xml.bind.JAXBElement;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.AvailabilityCondition;
import org.rutebanken.netex.model.ContactStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.Organisation_VersionStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.ValidityConditions_RelStructure;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ExportsService {

    /**
     * This is hardcoded until we can figure out a better strategy for its management.
     */
    private static final String NETEX_ROOT_ID = "FSR";

    private final CompanyRepository companyRepository;

    private final FintrafficIdService fintrafficIdService;

    private final NetexObjectFactory netexObjectFactory;

    public ExportsService(CompanyRepository companyRepository,
                          NetexObjectFactory netexObjectFactory,
                          FintrafficIdService fintrafficIdService) {
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.netexObjectFactory = Objects.requireNonNull(netexObjectFactory);
        this.fintrafficIdService = Objects.requireNonNull(fintrafficIdService);
    }

    public JAXBElement<PublicationDeliveryStructure> netexOrganisations() {
        // XXX: This is a quick hack for now, we have better reimplementation in backlog as TIS-878
        List<Organisation_VersionStructure> organisations = new ArrayList<>();
        AtomicInteger availabilityConditionCounter = new AtomicInteger(1);
        organisations.addAll(Streams.collect(companyRepository.listAll(), companyRecord -> asAuthority(availabilityConditionCounter, companyRecord)));
        organisations.addAll(Streams.collect(companyRepository.listAll(), companyRecord -> asOperator(availabilityConditionCounter, companyRecord)));

        ResourceFrame compositeFrame = netexObjectFactory.createResourceFrame(
            NETEX_ROOT_ID + ":ResourceFrame:1",
            organisations);

        return netexObjectFactory.createPublicationDelivery(
            "1.08:NO-NeTEx-fares:1.0",
            NETEX_ROOT_ID,
            compositeFrame,
            LocalDateTime.now());
    }

    private Authority asAuthority(AtomicInteger availabilityConditionCounter, CompanyRecord companyRecord) {
        Authority authority = new Authority()
            .withId(NETEX_ROOT_ID + ":Authority:" + companyRecord.businessId())
            .withName(multilingualString(companyRecord.name(), companyRecord.language()))
            .withCompanyNumber(companyRecord.businessId());
        ContactStructure contactStructure = createContactStructure(companyRecord);
        return authority.withValidityConditions(createValidityConditions(availabilityConditionCounter, companyRecord))
            .withContactDetails(contactStructure);
    }

    private Operator asOperator(AtomicInteger availabilityConditionCounter, CompanyRecord companyRecord) {
        Operator operator = new Operator()
            .withId(NETEX_ROOT_ID + ":Operator:" + companyRecord.businessId())
            .withName(multilingualString(companyRecord.name(), companyRecord.language()))
            .withCompanyNumber(companyRecord.businessId());
        ContactStructure contactStructure = createContactStructure(companyRecord);
        return operator.withValidityConditions(createValidityConditions(availabilityConditionCounter, companyRecord))
            .withContactDetails(contactStructure);
    }

    private ValidityConditions_RelStructure createValidityConditions(AtomicInteger availabilityConditionCounter, CompanyRecord companyRecord) {
        return new ValidityConditions_RelStructure().withValidityConditionRefOrValidBetweenOrValidityCondition_(
            new AvailabilityCondition()
                .withId(NETEX_ROOT_ID + ":AvailabilityCondition:" + availabilityConditionCounter.getAndIncrement())
                .withFromDate(LocalDateTime.of(2020, 1, 1, 0, 0)));
    }

    private ContactStructure createContactStructure(CompanyRecord companyRecord) {
        ContactStructure contactStructure = new ContactStructure()
            .withUrl(companyRecord.website());
        Optional.ofNullable(companyRecord.adGroupId())
            .flatMap(fintrafficIdService::getGroup)
            .flatMap(FintrafficIdGroup::organizationData)
            .ifPresent(organizationData ->
                contactStructure.withPhone(organizationData.phoneNumber())
                    .withContactPerson(multilingualString(organizationData.contactName(), companyRecord.language())));
        return contactStructure;
    }

    private static MultilingualString multilingualString(String organizationData, String language) {
        return new MultilingualString()
            .withValue(organizationData)
            .withLang(language);
    }

}
