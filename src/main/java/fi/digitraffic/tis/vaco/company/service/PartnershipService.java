package fi.digitraffic.tis.vaco.company.service;

import fi.digitraffic.tis.vaco.caching.CachingService;
import fi.digitraffic.tis.vaco.company.model.Company;
import fi.digitraffic.tis.vaco.company.model.Partnership;
import fi.digitraffic.tis.vaco.company.model.PartnershipType;
import fi.digitraffic.tis.vaco.company.model.ImmutablePartnership;
import fi.digitraffic.tis.vaco.company.repository.PartnershipRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class PartnershipService {

    private final CachingService cachingService;
    private final PartnershipRepository partnershipRepository;

    public PartnershipService(CachingService cachingService, PartnershipRepository partnershipRepository) {
        this.cachingService = Objects.requireNonNull(cachingService);
        this.partnershipRepository = Objects.requireNonNull(partnershipRepository);
    }

    public Optional<Partnership> create(PartnershipType partnershipType, Company partnerA, Company partnerB) {
        if (partnershipRepository.findByIds(partnershipType, partnerA.id(), partnerB.id()).isPresent()) {
            return Optional.empty();
        }

        Optional<Partnership> partnership = Optional.of(partnershipRepository.create(
            ImmutablePartnership.of(
                partnershipType,
                partnerA,
                partnerB)));
        cachingService.invalidateCompanyHierarchy(partnerA, "descendants");
        cachingService.invalidateCompanyHierarchy(partnerB, "descendants");
        return partnership;
    }
}
