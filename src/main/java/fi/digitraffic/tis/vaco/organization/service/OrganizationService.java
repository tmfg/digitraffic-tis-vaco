package fi.digitraffic.tis.vaco.organization.service;

import fi.digitraffic.tis.vaco.organization.model.Organization;
import fi.digitraffic.tis.vaco.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Optional<Organization> createOrganization(Organization organization) {
        Optional<Organization> existing = organizationRepository.findByBusinessId(organization.businessId());
        if (existing.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(organizationRepository.create(organization));
    }

    public Optional<Organization> findByBusinessId(String businessId) {
        return organizationRepository.findByBusinessId(businessId);
    }

    public List<Organization> listAllWithEntries() {
        return organizationRepository.listAllWithEntries();
    }
}
