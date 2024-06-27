package fi.digitraffic.tis.vaco.exports;

import fi.digitraffic.tis.vaco.db.repositories.CompanyRepository;
import fi.digitraffic.tis.vaco.exports.utils.NetexObjectFactory;
import jakarta.xml.bind.JAXBElement;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.GeneralOrganisation;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class ExportsService {

    private final CompanyRepository companyRepository;
    private final NetexObjectFactory netexObjectFactory;

    public ExportsService(CompanyRepository companyRepository, NetexObjectFactory netexObjectFactory) {
        this.companyRepository = companyRepository;
        this.netexObjectFactory = netexObjectFactory;
    }

    public JAXBElement<PublicationDeliveryStructure> netexOrganisations() {
        Collection<Authority> authorities = List.of();
        Collection<Operator> operators = List.of();
        List<GeneralOrganisation> organisations = List.of(new GeneralOrganisation());
        ResourceFrame compositeFrame = netexObjectFactory.createResourceFrame("FSR:ResourceFrame:1", authorities, operators);

        return netexObjectFactory.createPublicationDelivery("1.08:NO-NeTEx-fares:1.0", "FSR", compositeFrame, LocalDateTime.now());
    }

}
