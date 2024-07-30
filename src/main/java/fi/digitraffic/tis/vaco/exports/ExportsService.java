package fi.digitraffic.tis.vaco.exports;

import fi.digitraffic.tis.utilities.Streams;
import fi.digitraffic.tis.vaco.db.model.CompanyRecord;
import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.exports.utils.NetexObjectFactory;
import fi.digitraffic.tis.vaco.fintrafficid.FintrafficIdService;
import fi.digitraffic.tis.vaco.fintrafficid.model.FintrafficIdGroup;
import jakarta.xml.bind.JAXBElement;
import org.rutebanken.netex.model.ContactStructure;
import org.rutebanken.netex.model.GeneralOrganisation;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Organisation_VersionStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

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
        Collection<Organisation_VersionStructure> organisations = Streams.collect(
            companyRepository.listAll(),
            this::asNetexOrganisation);

        ResourceFrame compositeFrame = netexObjectFactory.createResourceFrame(
            NETEX_ROOT_ID + ":ResourceFrame:1",
            organisations);

        return netexObjectFactory.createPublicationDelivery(
            "1.08:NO-NeTEx-fares:1.0",
            NETEX_ROOT_ID,
            compositeFrame,
            LocalDateTime.now());
    }

    /**
     * @see <a href="https://enturas.atlassian.net/wiki/spaces/PUBLIC/pages/728727624/framework#Organisation">NeTEx Nordic: Organisation</a>
     * @param companyRecord Company details to be converted
     * @return converted {@link GeneralOrganisation}
     */
    private GeneralOrganisation asNetexOrganisation(CompanyRecord companyRecord) {
        GeneralOrganisation organisation = new GeneralOrganisation()
            .withId(NETEX_ROOT_ID + ":GeneralOrganisation:" + companyRecord.businessId())
            .withName(multilingualString(companyRecord.name(), companyRecord.language()))
            .withCompanyNumber(companyRecord.businessId());
        Optional.ofNullable(companyRecord.adGroupId())
            .flatMap(fintrafficIdService::getGroup)
            .flatMap(FintrafficIdGroup::organizationData)
            .ifPresent(organizationData ->
                organisation.withContactDetails(new ContactStructure()
                    .withPhone(organizationData.phoneNumber())
                    .withContactPerson(multilingualString(organizationData.contactName(), companyRecord.language()))));
        return organisation;
    }

    private static MultilingualString multilingualString(String organizationData, String language) {
        return new MultilingualString()
            .withValue(organizationData)
            .withLang(language);
    }

}
