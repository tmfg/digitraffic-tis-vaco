package fi.digitraffic.tis.vaco.tis.service;

import fi.digitraffic.tis.vaco.ItemExistsException;
import fi.digitraffic.tis.vaco.tis.model.ImmutableOrganization;
import fi.digitraffic.tis.vaco.tis.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public ImmutableOrganization createOrganization(ImmutableOrganization organization) {
        Optional<ImmutableOrganization> existing = organizationRepository.findByBusinessId(organization.businessId());
        if(existing.isPresent()) {
            throw new ItemExistsException("An organization with given business ID already exists");
        }
        return organizationRepository.create(organization);
    }

    public ImmutableOrganization getByBusinessId(String businessId) {
        return organizationRepository.getByBusinessId(businessId);
    }
}
