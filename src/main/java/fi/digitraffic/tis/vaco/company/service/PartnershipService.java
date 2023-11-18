package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.repository.PartnershipRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PartnershipService {

    private final PartnershipRepository partnershipRepository;

    public PartnershipService(PartnershipRepository partnershipRepository) {
        this.partnershipRepository = partnershipRepository;
    }

    public Optional<Partnership> create(PartnershipType partnershipType, Company partnerA, Company partnerB) {
        if(partnershipRepository.findByIds(partnershipType, partnerA.id(), partnerB.id()).isPresent()) {
            return Optional.empty();
        }

        return Optional.of(partnershipRepository.create(
            ImmutablePartnership.of(
                partnershipType,
                partnerA,
                partnerB)));
    }
}
