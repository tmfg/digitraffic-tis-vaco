package fi.digitraffic.tis.vaco.organization.service;

import fi.digitraffic.tis.vaco.organization.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Optional<ImmutableOrganization> createOrganization(ImmutableOrganization organization) {
        Optional<ImmutableOrganization> existing = organizationRepository.findByBusinessId(organization.businessId());
        if(existing.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(organizationRepository.create(organization));
    }

    public Optional<ImmutableOrganization> findByBusinessId(String businessId) {
        return organizationRepository.findByBusinessId(businessId);
    }
}
