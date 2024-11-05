package fi.digitraffic.tis.vaco.exports;

import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.exports.utils.NetexObjectFactory;
import fi.digitraffic.tis.vaco.fintrafficid.FintrafficIdService;
import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;
import jakarta.xml.bind.JAXBElement;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.ContactStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.Organisation_VersionStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.ValidBetween;
import org.rutebanken.netex.model.ValidityConditions_RelStructure;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class ExportsService {

    /**
     * This is hardcoded until we can figure out a better strategy for its management.
     */
    private static final String NETEX_ROOT_ID = "FSR";

    /**
     * NeTEx supports versioning all elements, but we don't have version history available so this value will be
     * hardcoded to be simply <code>"1"</code>.
     */
    private static final String ORGANISATION_VERSION = "1";

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
        AtomicInteger availabilityConditionCounter = new AtomicInteger(1);
        List<Organisation_VersionStructure> organisations = new ArrayList<>();
        companyRepository.listAll().forEach(companyRecord -> {
            asAuthority(availabilityConditionCounter, companyRecord).ifPresent(organisations::add);
            asOperator(availabilityConditionCounter, companyRecord).ifPresent(organisations::add);
        });

        ResourceFrame compositeFrame = netexObjectFactory.createResourceFrame(
            NETEX_ROOT_ID + ":ResourceFrame:1",
            organisations);

        return netexObjectFactory.createPublicationDelivery(
            "1.08:NO-NeTEx-fares:1.0",
            NETEX_ROOT_ID,
            compositeFrame,
            LocalDateTime.now());
    }

    private <O extends Organisation_VersionStructure> O createOrganisation(
        Supplier<O> organisation,
        CompanyRecord companyRecord) {
        O org = organisation.get();
        org.withId(NETEX_ROOT_ID + ":" + org.getClass().getSimpleName() + ":" + companyRecord.businessId())
            .withVersion(ORGANISATION_VERSION)
            .withName(multilingualString(companyRecord.name(), companyRecord.language()))
            .withCompanyNumber(companyRecord.businessId());
        return org;
    }

    private Optional<Organisation_VersionStructure> asAuthority(AtomicInteger availabilityConditionCounter, CompanyRecord companyRecord) {
        return createContactStructure(companyRecord)
            .map(contactStructure -> {
                Authority authority = createOrganisation(Authority::new, companyRecord);
                return authority.withValidityConditions(createValidityConditions(availabilityConditionCounter, companyRecord))
                    .withContactDetails(contactStructure);
            });
    }

    private Optional<Organisation_VersionStructure> asOperator(AtomicInteger availabilityConditionCounter, CompanyRecord companyRecord) {
        return createContactStructure(companyRecord)
            .map(contactStructure -> {
                Operator operator = createOrganisation(Operator::new, companyRecord);
                return operator.withValidityConditions(createValidityConditions(availabilityConditionCounter, companyRecord))
                    .withContactDetails(contactStructure);
            });
    }

    private ValidityConditions_RelStructure createValidityConditions(AtomicInteger availabilityConditionCounter, CompanyRecord companyRecord) {
        return new ValidityConditions_RelStructure().withValidityConditionRefOrValidBetweenOrValidityCondition_(
            new ValidBetween()
                .withId(NETEX_ROOT_ID + ":AvailabilityCondition:" + availabilityConditionCounter.getAndIncrement())
                .withFromDate(LocalDateTime.of(2020, 1, 1, 0, 0)));
    }

    private Optional<ContactStructure> createContactStructure(CompanyRecord companyRecord) {
        if (companyRecord.website() == null || companyRecord.website().isBlank()) {
            return Optional.empty();
        }
        ContactStructure contactStructure = new ContactStructure()
            .withUrl(companyRecord.website());
        Optional.ofNullable(companyRecord.adGroupId())
            .flatMap(fintrafficIdService::getGroup)
            .flatMap(FintrafficIdGroup::organizationData)
            .ifPresent(organizationData ->
                contactStructure.withPhone(organizationData.phoneNumber())
                    .withContactPerson(multilingualString(organizationData.contactName(), companyRecord.language())));
        return Optional.of(contactStructure);
    }

    private static MultilingualString multilingualString(String organizationData, String language) {
        return new MultilingualString()
            .withValue(organizationData)
            .withLang(language);
    }

}
